/**
 * Copyright 2012-2013 eBay Software Foundation
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.ebay.web.cors;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * <p>
 * A {@link Filter} that enable client-side cross-origin requests by
 * implementing W3C's CORS (<b>C</b>ross-<b>O</b>rigin <b>R</b>esource
 * <b>S</b>haring) speficiation for resources. Each {@link HttpServletRequest}
 * request is inspected as per specification, and appropriate response headers
 * are added to {@link HttpServletResponse}.
 * </p>
 * 
 * <p>
 * By default, it also sets following request attributes, that helps to
 * determine nature of request downstream.
 * <ul>
 * <li><b>cors.isCorsRequest:</b> Flag to determine if request is a CORS
 * request. Set to <code>true</code> if CORS request; <code>false</code>
 * otherwise.</li>
 * <li><b>cors.request.origin:</b> The Origin URL.</li>
 * <li><b>cors.request.type:</b> Type of request. Values: <code>simple</code> or
 * <code>preflight</code> or <code>not_cors</code> or <code>invalid_cors</code></li>
 * <li><b>cors.request.headers:</b> Request headers sent as
 * 'Access-Control-Request-Headers' header, for pre-flight request.</li>
 * </ul>
 * </p>
 * 
 * @author Mohit Soni
 * @see <a href="http://www.w3.org/TR/cors/">CORS specification</a>
 * 
 */
public class CORSFilter implements Filter {
    // -------------------------------------------------- CORS Response Headers
    /**
     * The Access-Control-Allow-Origin header indicates whether a resource can
     * be shared based by returning the value of the Origin request header in
     * the response.
     */
    public static final String RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_ORIGIN =
            "Access-Control-Allow-Origin";

    /**
     * The Access-Control-Allow-Credentials header indicates whether the
     * response to request can be exposed when the omit credentials flag is
     * unset. When part of the response to a preflight request it indicates that
     * the actual request can include user credentials.
     */
    public static final String RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_CREDENTIALS =
            "Access-Control-Allow-Credentials";

    /**
     * The Access-Control-Expose-Headers header indicates which headers are safe
     * to expose to the API of a CORS API specification
     */
    public static final String RESPONSE_HEADER_ACCESS_CONTROL_EXPOSE_HEADERS =
            "Access-Control-Expose-Headers";

    /**
     * The Access-Control-Max-Age header indicates how long the results of a
     * preflight request can be cached in a preflight result cache.
     */
    public static final String RESPONSE_HEADER_ACCESS_CONTROL_MAX_AGE =
            "Access-Control-Max-Age";

    /**
     * The Access-Control-Allow-Methods header indicates, as part of the
     * response to a preflight request, which methods can be used during the
     * actual request.
     */
    public static final String RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_METHODS =
            "Access-Control-Allow-Methods";

    /**
     * The Access-Control-Allow-Headers header indicates, as part of the
     * response to a preflight request, which header field names can be used
     * during the actual request.
     */
    public static final String RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_HEADERS =
            "Access-Control-Allow-Headers";

    // -------------------------------------------------- CORS Request Headers
    /**
     * The Origin header indicates where the cross-origin request or preflight
     * request originates from.
     */
    public static final String REQUEST_HEADER_ORIGIN = "Origin";

    /**
     * The Access-Control-Request-Method header indicates which method will be
     * used in the actual request as part of the preflight request.
     */
    public static final String REQUEST_HEADER_ACCESS_CONTROL_REQUEST_METHOD =
            "Access-Control-Request-Method";

    /**
     * The Access-Control-Request-Headers header indicates which headers will be
     * used in the actual request as part of the preflight request.
     */
    public static final String REQUEST_HEADER_ACCESS_CONTROL_REQUEST_HEADERS =
            "Access-Control-Request-Headers";

    // ----------------------------------------------------- Request attributes
    /**
     * The prefix to a CORS request attribute.
     */
    public static final String HTTP_REQUEST_ATTRIBUTE_PREFIX = "cors.";

    /**
     * Attribute that contains the origin of the request.
     */
    public static final String HTTP_REQUEST_ATTRIBUTE_ORIGIN =
            HTTP_REQUEST_ATTRIBUTE_PREFIX + "request.origin";

    /**
     * Boolean value, suggesting if the request is a CORS request or not.
     */
    public static final String HTTP_REQUEST_ATTRIBUTE_IS_CORS_REQUEST =
            HTTP_REQUEST_ATTRIBUTE_PREFIX + "isCorsRequest";

    /**
     * Type of CORS request, of type {@link CORSRequestType}.
     */
    public static final String HTTP_REQUEST_ATTRIBUTE_REQUEST_TYPE =
            HTTP_REQUEST_ATTRIBUTE_PREFIX + "request.type";

    /**
     * Request headers sent as 'Access-Control-Request-Headers' header, for
     * pre-flight request.
     */
    public static final String HTTP_REQUEST_ATTRIBUTE_REQUEST_HEADERS =
            HTTP_REQUEST_ATTRIBUTE_PREFIX + "request.headers";

    // -------------------------------------------------------------- Constants
    /**
     * Enumerates varies types of CORS requests. Also, provides utility methods
     * to determine the request type.
     */
    public static enum CORSRequestType {
        /**
         * A simple HTTP request, i.e. it shouldn't be pre-flighted.
         */
        SIMPLE,
        /**
         * A HTTP request that needs to be pre-flighted.
         */
        ACTUAL,
        /**
         * A pre-flight CORS request, to get meta information, before a
         * non-simple HTTP request is sent.
         */
        PRE_FLIGHT,
        /**
         * Not a CORS request, but a normal request.
         */
        NOT_CORS,
        /**
         * An invalid CORS request, i.e. it qualifies to be a CORS request, but
         * fails to be a valid one.
         */
        INVALID_CORS;
    }

    /**
     * {@link Collection} of HTTP methods. Case sensitive.
     * 
     * @see http://tools.ietf.org/html/rfc2616#section-5.1.1
     */
    public static final Set<String> HTTP_METHODS = new HashSet<String>(
            Arrays.asList("OPTIONS", "GET", "HEAD", "POST", "PUT", "DELETE",
                    "TRACE", "CONNECT"));
    /**
     * {@link Collection} of non-simple HTTP methods. Case sensitive.
     */
    public static final Set<String> COMPLEX_HTTP_METHODS = new HashSet<String>(
            Arrays.asList("PUT", "DELETE", "TRACE", "CONNECT"));
    /**
     * {@link Collection} of Simple HTTP methods. Case sensitive.
     * 
     * @see http://www.w3.org/TR/cors/#terminology
     */
    public static final Set<String> SIMPLE_HTTP_METHODS = new HashSet<String>(
            Arrays.asList("GET", "POST", "HEAD"));

    /**
     * {@link Collection} of Simple HTTP request headers. Case in-sensitive.
     * 
     * @see http://www.w3.org/TR/cors/#terminology
     */
    public static final Set<String> SIMPLE_HTTP_REQUEST_HEADERS =
            new HashSet<String>(Arrays.asList("Accept", "Accept-Language",
                    "Content-Language"));

    /**
     * {@link Collection} of Simple HTTP request headers. Case in-sensitive.
     * 
     * @see http://www.w3.org/TR/cors/#terminology
     */
    public static final Set<String> SIMPLE_HTTP_RESPONSE_HEADERS =
            new HashSet<String>(Arrays.asList("Cache-Control",
                    "Content-Language", "Content-Type", "Expires",
                    "Last-Modified", "Pragma"));

    /**
     * A Simple HTTP request header, if the header values matches
     * {@code SIMPLE_HTTP_REQUEST_CONTENT_TYPE_VALUES}. Case in-sensitive.
     * 
     * @see http://www.w3.org/TR/cors/#terminology
     */
    public static final String SIMPLE_REQUEST_HEADER_CONTENT_TYPE =
            "Content-Type";

    /**
     * {@link Collection} of Simple HTTP request headers. Case in-sensitive.
     * 
     * @see http://www.w3.org/TR/cors/#terminology
     */
    public static final Set<String> SIMPLE_HTTP_REQUEST_CONTENT_TYPE_VALUES =
            new HashSet<String>(Arrays.asList(
                    "application/x-www-form-urlencoded", "multipart/form-data",
                    "text/plain"));

    // ------------------------------------------------ Configuration Defaults
    /**
     * By default, all origins are allowed to make requests.
     */
    public static final String DEFAULT_ALLOWED_ORIGINS = "*";

    /**
     * By default, following methods are supported: GET, POST, HEAD and OPTIONS.
     */
    public static final String DEFAULT_ALLOWED_HTTP_METHODS =
            "GET,POST,HEAD,OPTIONS";

    /**
     * By default, time duration to cache pre-flight response is 30 mins.
     */
    public static final String DEFAULT_PREFLIGHT_MAXAGE = "1800";

    /**
     * By default, cookie based auth is turned off.
     */
    public static final String DEFAULT_SUPPORTS_CREDENTIALS = "false";

    /**
     * By default, following headers are supported:
     * Origin,Accept,X-Requested-With, and Content-Type.
     */
    public static final String DEFAULT_ALLOWED_HTTP_HEADERS =
            "Origin,Accept,X-Requested-With,Content-Type";

    /**
     * By default, none of the headers are exposed in response.
     */
    public static final String DEFAULT_EXPOSED_HEADERS = "";

    /**
     * By default, access log logging is turned off
     */
    public static final String DEFAULT_LOGGING_ENABLED = "false";

    // ----------------------------------------Filter Config Init param-name(s)
    /**
     * Key to retrieve allowed origins from {@link FilterConfig}.
     */
    public static final String PARAM_CORS_ALLOWED_ORIGINS =
            "cors.allowed.origins";

    /**
     * Key to retrieve support credentials from {@link FilterConfig}.
     */
    public static final String PARAM_CORS_SUPPORT_CREDENTIALS =
            "cors.support.credentials";

    /**
     * Key to retrieve exposed headers from {@link FilterConfig}.
     */
    public static final String PARAM_CORS_EXPOSED_HEADERS =
            "cors.exposed.headers";

    /**
     * Key to retrieve allowed headers from {@link FilterConfig}.
     */
    public static final String PARAM_CORS_ALLOWED_HEADERS =
            "cors.allowed.headers";

    /**
     * Key to retrieve allowed methods from {@link FilterConfig}.
     */
    public static final String PARAM_CORS_ALLOWED_METHODS =
            "cors.allowed.methods";

    /**
     * Key to retrieve preflight max age from {@link FilterConfig}.
     */
    public static final String PARAM_CORS_PREFLIGHT_MAXAGE =
            "cors.preflight.maxage";

    /**
     * Key to retrieve access log logging flag.
     */
    public static final String PARAM_CORS_LOGGING_ENABLED =
            "cors.logging.enabled";

    // ----------------------------------------------------- Instance variables
    /**
     * Holds filter configuration.
     */
    private FilterConfig filterConfig;

    /**
     * A {@link Collection} of origins consisting of zero or more origins that
     * are allowed access to the resource.
     */
    private final Set<String> allowedOrigins;

    /**
     * Determines if any origin is allowed to make request.
     */
    private boolean anyOriginAllowed;

    /**
     * A {@link Collection} of methods consisting of zero or more methods that
     * are supported by the resource.
     */
    private final Set<String> allowedHttpMethods;

    /**
     * A {@link Collection} of headers consisting of zero or more header field
     * names that are supported by the resource.
     */
    private final Set<String> allowedHttpHeaders;

    /**
     * A {@link Collection} of exposed headers consisting of zero or more header
     * field names of headers other than the simple response headers that the
     * resource might use and can be exposed.
     */
    private Set<String> exposedHeaders;

    /**
     * A supports credentials flag that indicates whether the resource supports
     * user credentials in the request. It is true when the resource does and
     * false otherwise.
     */
    private boolean supportsCredentials;

    /**
     * Indicates (in seconds) how long the results of a pre-flight request can
     * be cached in a pre-flight result cache.
     */
    private long preflightMaxAge;

    /**
     * Controls access log logging.
     */
    private boolean loggingEnabled;

    // --------------------------------------------------------- Constructor(s)
    public CORSFilter() {
        this.allowedOrigins = new HashSet<String>();
        this.allowedHttpMethods = new HashSet<String>();
        this.allowedHttpHeaders = new HashSet<String>();
        this.exposedHeaders = new HashSet<String>();
    }

    // --------------------------------------------------------- Public methods
    public void doFilter(final ServletRequest servletRequest,
            final ServletResponse servletResponse,
            final FilterChain filterChain) throws IOException,
            ServletException {
        if (!(servletRequest instanceof HttpServletRequest)
                || !(servletResponse instanceof HttpServletResponse)) {
            String message =
                    "CORS doesn't support non-HTTP request or response.";
            throw new ServletException(message);
        }

        // Safe to downcast at this point.
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // Determines the CORS request type.
        CORSFilter.CORSRequestType requestType = checkRequestType(request);

        // Adds CORS specific attributes to request.
        CORSFilter.decorateCORSProperties(request, requestType);

        switch (requestType) {
        case SIMPLE:
            // Handles a Simple CORS request.
            this.handleSimpleCORS(request, response, filterChain);
            break;
        case ACTUAL:
            // Handles an Actual CORS request.
            this.handleSimpleCORS(request, response, filterChain);
            break;
        case PRE_FLIGHT:
            // Handles a Pre-flight CORS request.
            this.handlePreflightCORS(request, response, filterChain);
            break;
        case NOT_CORS:
            // Handles a Normal request that is not a cross-origin request.
            this.handleNonCORS(request, response, filterChain);
            break;
        default:
            // Handles a CORS request that violates specification.
            this.handleInvalidCORS(request, response, filterChain);
            break;
        }
    }

    public void init(final FilterConfig filterConfig) throws ServletException {
        // Initialize defaults
        parseAndStore(DEFAULT_ALLOWED_ORIGINS, DEFAULT_ALLOWED_HTTP_METHODS,
                DEFAULT_ALLOWED_HTTP_HEADERS, DEFAULT_EXPOSED_HEADERS,
                DEFAULT_SUPPORTS_CREDENTIALS, DEFAULT_PREFLIGHT_MAXAGE,
                DEFAULT_LOGGING_ENABLED);

        this.filterConfig = filterConfig;
        this.loggingEnabled = false;

        if (filterConfig != null) {
            String allowedOrigins =
                    filterConfig.getInitParameter(PARAM_CORS_ALLOWED_ORIGINS);
            String allowedHttpMethods =
                    filterConfig.getInitParameter(PARAM_CORS_ALLOWED_METHODS);
            String allowedHttpHeaders =
                    filterConfig.getInitParameter(PARAM_CORS_ALLOWED_HEADERS);
            String exposedHeaders =
                    filterConfig.getInitParameter(PARAM_CORS_EXPOSED_HEADERS);
            String supportsCredentials =
                    filterConfig
                            .getInitParameter(PARAM_CORS_SUPPORT_CREDENTIALS);
            String preflightMaxAge =
                    filterConfig.getInitParameter(PARAM_CORS_PREFLIGHT_MAXAGE);
            String loggingEnabled =
                    filterConfig.getInitParameter(PARAM_CORS_LOGGING_ENABLED);

            parseAndStore(allowedOrigins, allowedHttpMethods,
                    allowedHttpHeaders,
                    exposedHeaders, supportsCredentials, preflightMaxAge,
                    loggingEnabled);
        }
    }

    /**
     * Handles a CORS request of type {@link CORSRequestType}.SIMPLE.
     * 
     * @param request
     *            The {@link HttpServletRequest} object.
     * @param response
     *            The {@link HttpServletResponse} object.
     * @param filterChain
     *            The {@link FilterChain} object.
     * @throws IOException
     * @throws ServletException
     * @see <a href="http://www.w3.org/TR/cors/#resource-requests">Simple
     *      Cross-Origin Request, Actual Request, and Redirects</a>
     */
    public void handleSimpleCORS(final HttpServletRequest request,
            final HttpServletResponse response, final FilterChain filterChain)
            throws IOException, ServletException {
        CORSFilter.CORSRequestType requestType =
                checkRequestType(request);
        if (!(requestType == CORSFilter.CORSRequestType.SIMPLE
        || requestType == CORSFilter.CORSRequestType.ACTUAL)) {
            String message =
                    "Expects a HttpServletRequest object of type "
                            + CORSFilter.CORSRequestType.SIMPLE
                            + " or "
                            + CORSFilter.CORSRequestType.ACTUAL;
            throw new IllegalArgumentException(message);
        }

        final String origin =
                request.getHeader(CORSFilter.REQUEST_HEADER_ORIGIN);
        final String method = request.getMethod();

        // Section 6.1.2
        if (!isOriginAllowed(origin)) {
            handleInvalidCORS(request, response, filterChain);
            return;
        }

        if (!allowedHttpMethods.contains(method)) {
            handleInvalidCORS(request, response, filterChain);
            return;
        }

        // Section 6.1.3
        // Add a single Access-Control-Allow-Origin header.
        if (anyOriginAllowed && !supportsCredentials) {
            // If resource doesn't support credentials and if any origin is
            // allowed
            // to make CORS request, return header with '*'.
            response.addHeader(
                    CORSFilter.RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        } else {
            // If the resource supports credentials add a single
            // Access-Control-Allow-Origin header, with the value of the Origin
            // header as value.
            response.addHeader(
                    CORSFilter.RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_ORIGIN,
                    origin);
        }
        // Section 6.1.3
        // If the resource supports credentials, add a single
        // Access-Control-Allow-Credentials header with the case-sensitive
        // string "true" as value.
        if (supportsCredentials) {
            response.addHeader(
                    CORSFilter.RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_CREDENTIALS,
                    "true");
        }

        // Section 6.1.4
        // If the list of exposed headers is not empty add one or more
        // Access-Control-Expose-Headers headers, with as values the header
        // field names given in the list of exposed headers.
        if ((exposedHeaders != null) && (exposedHeaders.size() > 0)) {
            String exposedHeadersString = join(exposedHeaders, ",");
            response.addHeader(
                    CORSFilter.RESPONSE_HEADER_ACCESS_CONTROL_EXPOSE_HEADERS,
                    exposedHeadersString);
        }

        // Forward the request down the filter chain.
        filterChain.doFilter(request, response);
    }

    /**
     * Handles CORS pre-flight request.
     * 
     * @param request
     *            The {@link HttpServletRequest} object.
     * @param response
     *            The {@link HttpServletResponse} object.
     * @param filterChain
     *            The {@link FilterChain} object.
     * @throws IOException
     * @throws ServletException
     */
    public void handlePreflightCORS(final HttpServletRequest request,
            final HttpServletResponse response, final FilterChain filterChain)
            throws IOException, ServletException {
        CORSRequestType requestType = checkRequestType(request);
        if (requestType != CORSRequestType.PRE_FLIGHT) {
            throw new IllegalArgumentException(
                    "Expects a HttpServletRequest object of type "
                            + CORSRequestType.PRE_FLIGHT.name().toLowerCase());
        }

        final String origin =
                request.getHeader(CORSFilter.REQUEST_HEADER_ORIGIN);

        // Section 6.2.2
        if (!isOriginAllowed(origin)) {
            handleInvalidCORS(request, response, filterChain);
            return;
        }

        // Section 6.2.3
        String accessControlRequestMethod =
                request.getHeader(CORSFilter.REQUEST_HEADER_ACCESS_CONTROL_REQUEST_METHOD);
        if (accessControlRequestMethod == null
                || (accessControlRequestMethod != null && HTTP_METHODS
                        .contains(accessControlRequestMethod.trim()) == false)) {
            handleInvalidCORS(request, response, filterChain);
            return;
        } else {
            accessControlRequestMethod = accessControlRequestMethod.trim();
        }

        // Section 6.2.4
        String accessControlRequestHeadersHeader =
                request.getHeader(CORSFilter.REQUEST_HEADER_ACCESS_CONTROL_REQUEST_HEADERS);
        List<String> accessControlRequestHeaders = new LinkedList<String>();
        if (accessControlRequestHeadersHeader != null
                && !accessControlRequestHeadersHeader.trim().isEmpty()) {
            String[] headers =
                    accessControlRequestHeadersHeader.trim().split(",");
            for (String header : headers) {
                accessControlRequestHeaders.add(header.trim().toLowerCase());
            }
        }

        // Section 6.2.5
        if (!allowedHttpMethods.contains(accessControlRequestMethod)) {
            handleInvalidCORS(request, response, filterChain);
            return;
        }

        // Section 6.2.6
        if (!accessControlRequestHeaders.isEmpty()) {
            for (String header : accessControlRequestHeaders) {
                if (!allowedHttpHeaders.contains(header)) {
                    handleInvalidCORS(request, response, filterChain);
                    return;
                }
            }
        }

        // Section 6.2.7
        if (supportsCredentials) {
            response.addHeader(
                    CORSFilter.RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_ORIGIN,
                    origin);
            response.addHeader(
                    CORSFilter.RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_CREDENTIALS,
                    "true");
        } else {
            if (anyOriginAllowed) {
                response.addHeader(
                        CORSFilter.RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_ORIGIN,
                        "*");
            } else {
                response.addHeader(
                        CORSFilter.RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_ORIGIN,
                        origin);
            }
        }

        // Section 6.2.8
        if (preflightMaxAge > 0) {
            response.addHeader(
                    CORSFilter.RESPONSE_HEADER_ACCESS_CONTROL_MAX_AGE,
                    String.valueOf(preflightMaxAge));
        }

        // Section 6.2.9
        response.addHeader(
                CORSFilter.RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_METHODS,
                accessControlRequestMethod);

        // Section 6.2.10
        if ((allowedHttpHeaders != null) && (!allowedHttpHeaders.isEmpty())) {
            response.addHeader(
                    CORSFilter.RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_HEADERS,
                    join(allowedHttpHeaders, ","));
        }

        // Do not forward the request down the filter chain.
    }

    /**
     * Handles a request, that's not a CORS request, but is a valid request i.e.
     * it is not a cross-origin request. This implementation, just forwards the
     * request down the filter chain.
     * 
     * @param request
     *            The {@link HttpServletRequest} object.
     * @param response
     *            The {@link HttpServletResponse} object.
     * @param filterChain
     *            The {@link FilterChain} object.
     * @throws IOException
     * @throws ServletException
     */
    public void handleNonCORS(final HttpServletRequest request,
            final HttpServletResponse response, final FilterChain filterChain)
            throws IOException, ServletException {
        // Let request pass.
        filterChain.doFilter(request, response);
    }

    /**
     * Handles a CORS request that violates specification.
     * 
     * @param request
     *            The {@link HttpServletRequest} object.
     * @param response
     *            The {@link HttpServletResponse} object.
     * @param filterChain
     *            The {@link FilterChain} object.
     * @throws IOException
     * @throws ServletException
     */
    public void handleInvalidCORS(final HttpServletRequest request,
            final HttpServletResponse response, final FilterChain filterChain)
            throws IOException, ServletException {
        String origin = request.getHeader(CORSFilter.REQUEST_HEADER_ORIGIN);
        String method = request.getMethod();
        String message =
                "Encountered an invalid CORS request, from Origin: " + origin
                        + ", requested with method: " + method;
        response.setContentType("text/plain");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.resetBuffer();

        log(message);

        return;
    }

    public void destroy() {

    }

    // --------------------------------------------------------- Static methods
    /**
     * Decorates the {@link HttpServletRequest}, with CORS attributes.
     * <ul>
     * <li><b>cors.isCorsRequest:</b> Flag to determine if request is a CORS
     * request. Set to <code>true</code> if CORS request; <code>false</code>
     * otherwise.</li>
     * <li><b>cors.request.origin:</b> The Origin URL.</li>
     * <li><b>cors.request.type:</b> Type of request. Values:
     * <code>simple</code> or <code>preflight</code> or <code>not_cors</code> or
     * <code>invalid_cors</code></li>
     * <li><b>cors.request.headers:</b> Request headers sent as
     * 'Access-Control-Request-Headers' header, for pre-flight request.</li>
     * </ul>
     * 
     * @param request
     *            The {@link HttpServletRequest} object.
     * @param corsRequestType
     *            The {@link CORSRequestType} object.
     */
    public static void decorateCORSProperties(final HttpServletRequest request,
            final CORSRequestType corsRequestType) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "HttpServletRequest object is null");
        }

        if (corsRequestType == null) {
            throw new IllegalArgumentException("CORSRequestType object is null");
        }

        switch (corsRequestType) {
        case SIMPLE:
            request.setAttribute(
                    CORSFilter.HTTP_REQUEST_ATTRIBUTE_IS_CORS_REQUEST, true);
            request.setAttribute(CORSFilter.HTTP_REQUEST_ATTRIBUTE_ORIGIN,
                    request.getHeader(CORSFilter.REQUEST_HEADER_ORIGIN));
            request.setAttribute(
                    CORSFilter.HTTP_REQUEST_ATTRIBUTE_REQUEST_TYPE,
                    corsRequestType.name().toLowerCase());
            break;
        case ACTUAL:
            request.setAttribute(
                    CORSFilter.HTTP_REQUEST_ATTRIBUTE_IS_CORS_REQUEST, true);
            request.setAttribute(CORSFilter.HTTP_REQUEST_ATTRIBUTE_ORIGIN,
                    request.getHeader(CORSFilter.REQUEST_HEADER_ORIGIN));
            request.setAttribute(
                    CORSFilter.HTTP_REQUEST_ATTRIBUTE_REQUEST_TYPE,
                    corsRequestType.name().toLowerCase());
            break;
        case PRE_FLIGHT:
            request.setAttribute(
                    CORSFilter.HTTP_REQUEST_ATTRIBUTE_IS_CORS_REQUEST, true);
            request.setAttribute(CORSFilter.HTTP_REQUEST_ATTRIBUTE_ORIGIN,
                    request.getHeader(CORSFilter.REQUEST_HEADER_ORIGIN));
            request.setAttribute(
                    CORSFilter.HTTP_REQUEST_ATTRIBUTE_REQUEST_TYPE,
                    corsRequestType.name().toLowerCase());
            String headers =
                    request.getHeader(REQUEST_HEADER_ACCESS_CONTROL_REQUEST_HEADERS);
            if (headers == null) {
                headers = "";
            }
            request.setAttribute(
                    CORSFilter.HTTP_REQUEST_ATTRIBUTE_REQUEST_HEADERS,
                    headers
                    );
            break;
        case NOT_CORS:
            request.setAttribute(
                    CORSFilter.HTTP_REQUEST_ATTRIBUTE_IS_CORS_REQUEST, false);
            break;
        default:
            // Don't set any attributes
            break;
        }
    }

    /**
     * Joins elements of {@link Set} into a string, where each element is
     * separated by the provided separator.
     * 
     * @param elements
     *            The {@link Set} containing elements to join together.
     * @param joinSeparator
     *            The character to be used for separating elements.
     * @return The joined {@link String}; <code>null</code> if elements
     *         {@link Set} is null.
     */
    public static String join(final Set<String> elements,
            final String joinSeparator) {
        String separator = ",";
        if (elements == null) {
            return null;
        }
        if (joinSeparator != null) {
            separator = joinSeparator;
        }
        StringBuilder buffer = new StringBuilder();
        boolean isFirst = true;
        Iterator<String> iterator = elements.iterator();
        while (iterator.hasNext()) {
            Object element = iterator.next();

            if (!isFirst) {
                buffer.append(separator);
            } else {
                isFirst = false;
            }

            if (element != null) {
                buffer.append(element);
            }
        }

        return buffer.toString();
    }

    /**
     * Determines the request type.
     * 
     * @param request
     * @return
     */
    public CORSRequestType checkRequestType(final HttpServletRequest request) {
        CORSRequestType requestType = CORSRequestType.INVALID_CORS;
        if (request == null) {
            throw new IllegalArgumentException(
                    "HttpServletRequest object is null");
        }
        String originHeader = request.getHeader(REQUEST_HEADER_ORIGIN);
        // Section 6.1.1 and Section 6.2.1
        if (originHeader != null) {
            if (originHeader.isEmpty()) {
                requestType = CORSRequestType.INVALID_CORS;
            } else {
                String method = request.getMethod();
                if (method != null && HTTP_METHODS.contains(method)) {
                    if ("OPTIONS".equals(method)) {
                        requestType = CORSRequestType.PRE_FLIGHT;
                    } else if (COMPLEX_HTTP_METHODS.contains(method)) {
                        requestType = CORSRequestType.ACTUAL;
                    } else if ("GET".equals(method) || "HEAD".equals(method)) {
                        requestType = CORSRequestType.SIMPLE;
                    } else if ("POST".equals(method)) {
                        String contentType = request.getContentType();
                        if (contentType != null) {
                            contentType = contentType.toLowerCase().trim();
                            if (SIMPLE_HTTP_REQUEST_CONTENT_TYPE_VALUES
                                    .contains(contentType)) {
                                requestType = CORSRequestType.SIMPLE;
                            } else {
                                requestType = CORSRequestType.ACTUAL;
                            }
                        }
                    }
                }
            }
        } else {
            requestType = CORSRequestType.NOT_CORS;
        }

        return requestType;
    }

    /**
     * Checks if the Origin is allowed to make a CORS request.
     * 
     * @param origin
     *            The Origin.
     * @return <code>true</code> if origin is allowed; <code>false</code>
     *         otherwise.
     */
    private boolean isOriginAllowed(final String origin) {
        if (anyOriginAllowed) {
            return true;
        }

        // If 'Origin' header is a case-sensitive match of any of allowed
        // origins, then return true, else return false.
        if (allowedOrigins.contains(origin)) {
            return true;
        }

        return false;
    }

    private void log(String message) {
        if (loggingEnabled) {
            filterConfig.getServletContext().log(message);
        }
    }

    /**
     * Parses each param-value and populates configuration variables.
     * 
     * @param allowedOrigins
     *            A {@link String} of comma separated origins.
     * @param allowedHttpMethods
     *            A {@link String} of comma separated HTTP methods.
     * @param allowedHttpHeaders
     *            A {@link String} of comma separated HTTP headers.
     * @param exposedHeaders
     *            A {@link String} of comma separated headers that needs to be
     *            exposed.
     * @param supportsCredentials
     *            "true" if support credentials needs to be enabled.
     * @param preflightMaxAge
     *            The amount of seconds the user agent is allowed to cache the
     *            result of the pre-flight request.
     * @param loggingEnabled
     *            Flag to control logging to access log.
     * @throws ServletException
     */
    private void parseAndStore(final String allowedOrigins,
            final String allowedHttpMethods, final String allowedHttpHeaders,
            final String exposedHeaders, final String supportsCredentials,
            final String preflightMaxAge, final String loggingEnabled)
            throws ServletException {
        if (allowedOrigins != null) {
            if (allowedOrigins.trim().equals("*")) {
                this.anyOriginAllowed = true;
            } else {
                this.anyOriginAllowed = false;
                Set<String> setAllowedOrigins =
                        parseStringToSet(allowedOrigins);
                this.allowedOrigins.clear();
                this.allowedOrigins.addAll(setAllowedOrigins);
            }
        }

        if (allowedHttpMethods != null) {
            Set<String> setAllowedHttpMethods =
                    parseStringToSet(allowedHttpMethods);
            this.allowedHttpMethods.clear();
            this.allowedHttpMethods.addAll(setAllowedHttpMethods);
        }

        if (allowedHttpHeaders != null) {
            Set<String> setAllowedHttpHeaders =
                    parseStringToSet(allowedHttpHeaders);
            Set<String> lowerCaseHeaders = new HashSet<String>();
            for (String header : setAllowedHttpHeaders) {
                String lowerCase = header.toLowerCase();
                lowerCaseHeaders.add(lowerCase);
            }
            this.allowedHttpHeaders.clear();
            this.allowedHttpHeaders.addAll(lowerCaseHeaders);
        }

        if (exposedHeaders != null) {
            Set<String> setExposedHeaders = parseStringToSet(exposedHeaders);
            this.exposedHeaders.clear();
            this.exposedHeaders.addAll(setExposedHeaders);
        }

        if (supportsCredentials != null) {
            // For any value other then 'true' this will be false.
            boolean isSupportsCredentials =
                    Boolean.parseBoolean(supportsCredentials);
            this.supportsCredentials = isSupportsCredentials;
        }

        if (preflightMaxAge != null) {
            try {
                if (preflightMaxAge.isEmpty() == false) {
                    this.preflightMaxAge = Long.parseLong(preflightMaxAge);
                } else {
                    this.preflightMaxAge = 0L;
                }
            } catch (NumberFormatException e) {
                throw new ServletException("Unable to parse preflightMaxAge", e);
            }
        }

        if (loggingEnabled != null) {
            // For any value other then 'true' this will be false.
            boolean isLoggingEnabled =
                    Boolean.parseBoolean(loggingEnabled);
            this.loggingEnabled = isLoggingEnabled;
        }
    }

    /**
     * Takes a comma separated list and returns a Set<String>.
     * 
     * @param data
     *            A comma separated list of strings.
     * @return Set<String>
     */
    private Set<String> parseStringToSet(final String data) {
        String[] splits = null;

        if (data != null && data.length() > 0) {
            splits = data.split(",");
        } else {
            splits = new String[] {};
        }

        Set<String> set = new HashSet<String>();
        if (splits.length > 0) {
            for (String split : splits) {
                set.add(split.trim());
            }
        }

        return set;
    }

    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    public boolean isAnyOriginAllowed() {
        return anyOriginAllowed;
    }

    public Set<String> getExposedHeaders() {
        return exposedHeaders;
    }

    public boolean isSupportsCredentials() {
        return supportsCredentials;
    }

    public long getPreflightMaxAge() {
        return preflightMaxAge;
    }

    public Set<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public Set<String> getAllowedHttpMethods() {
        return allowedHttpMethods;
    }

    public Set<String> getAllowedHttpHeaders() {
        return allowedHttpHeaders;
    }

}
