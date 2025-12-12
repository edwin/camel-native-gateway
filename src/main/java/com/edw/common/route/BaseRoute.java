package com.edw.common.route;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;

import java.util.Map;

/**
 * <pre>
 *  com.edw.common.route.BaseRoute
 * </pre>
 *
 * Base route class to eliminate code duplication between route implementations
 */
public abstract class BaseRoute extends RouteBuilder {

    private Logger logger = org.slf4j.LoggerFactory.getLogger(BaseRoute.class);

    @Override
    public void configure() throws Exception {
        // Error Handler for unhandled exceptions
        onException(Exception.class)
                .handled(true)
                .process(exchange -> {
                    Throwable e = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                    if (e.getClass().getName().equals("org.apache.camel.processor.ThrottlerRejectedExecutionException")) {
                        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 429);
                        exchange.getIn().setBody(Map.of("error", "too many requests"));
                        logger.info("too many requests at {}", getRouteId());
                    } else {
                        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
                        exchange.getIn().setBody(Map.of("error", "gateway error"));
                        logger.info("gateway error at {}", getRouteId());
                    }
                })
                .marshal().json();

        from(getRouteUrl())
                .routeId(getRouteId())
                .log(LoggingLevel.DEBUG,"Received request : ${header.CamelHttpPath} - body : ${body}")

                // throttling
                .throttle(10) // 10 concurrent requests
                    .timePeriodMillis(1000) // 1 second
                    .rejectExecution(true)
                .end()

                // circuit breaker
                .circuitBreaker()
                    .faultToleranceConfiguration()
                        .timeoutEnabled(true)
                        .timeoutDuration(2000) // timeout in milliseconds
                        .requestVolumeThreshold(5) // open after 5 errors
                        .failureRatio(50)
                .end()

                // request to downstream service
                .process(this::prepareDownstreamRequest)
                .removeHeader(Exchange.HTTP_URI)
                .log(LoggingLevel.DEBUG, getDownstreamLogMessage())
                .to(getDownstreamUrl())
                .removeHeaders("(?i)(Forwarded|X-Forwarded.*|X-Envoy.*|Server|User-Agent|Accept|X-Request-Id|X-Powered-By)")
                .log(LoggingLevel.DEBUG, "response body is ${body}")

                // handle fallback
                .onFallback()
                    .log(LoggingLevel.INFO,"Circuit Breaker triggered! Service unavailable.")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(503))
                    .setBody(constant(Map.of("error", "service unavailable")))
                    .marshal().json()
                .end();
    }

    /**
     * Get the route URL for this service
     * @return the route URL
     */
    protected abstract String getRouteUrl();

    /**
     * Get the route ID for this service
     * @return the route ID
     */
    protected abstract String getRouteId();

    /**
     * Get the downstream service URL
     * @return the downstream URL
     */
    protected abstract String getDownstreamUrl();

    /**
     * Get the log message for downstream service call
     * @return the log message
     */
    protected abstract String getDownstreamLogMessage();

    /**
     * Prepare the request for the downstream service
     * This method can be overridden to customize the request
     * @param exchange the exchange
     */
    protected void prepareDownstreamRequest(Exchange exchange) {
        // Default implementation does nothing
        // Subclasses can override to add custom logic
    }
}