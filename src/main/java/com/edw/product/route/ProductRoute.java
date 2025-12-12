package com.edw.product.route;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.ThrottlerRejectedExecutionException;

import java.util.Map;

/**
 * <pre>
 *  com.edw.product.route.ProductRoute
 * </pre>
 *
 * @author Muhammad Edwin < edwin at redhat dot com >
 * 12 Dec 2025 16:48
 */
@ApplicationScoped
public class ProductRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // Error Handler for unhandled exceptions
        onException(Exception.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                .setBody(constant(Map.of("error", "gateway error")))
                .log(LoggingLevel.INFO,"gateway error")
                .marshal().json();

        onException(ThrottlerRejectedExecutionException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(429))
                .setBody(constant(Map.of("error", "too many requests")))
                .log(LoggingLevel.INFO,"too many requests")
                .marshal().json();

        from("platform-http:/api/v1/products?matchOnUriPrefix=true")
                .routeId("product-service-gateway")
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
                // changing from /api/v1/products to /api/product
                .setHeader(Exchange.HTTP_PATH, simple("${header.CamelHttpPath.replace('/v1/products', '/product')}"))
                .removeHeader(Exchange.HTTP_URI)
                    .log(LoggingLevel.DEBUG, "firing to downstream service {{downstream.service.url.product}}${header.CamelHttpPath}?${header.CamelHttpQuery}")
                .to("{{downstream.service.url.product}}/?bridgeEndpoint=true&throwExceptionOnFailure=true")
                .removeHeaders("(?i)(Forwarded|X-Forwarded.*|X-Envoy.*|Server|User-Agent|Accept|X-Request-Id|X-Powered-By)")
                    .log(LoggingLevel.DEBUG, "response body is ${body}")

                // handle fallback
                .onFallback()
                    .log("Failure Reason: ${exception.message}")
                    .log("Root Cause: ${exception.stacktrace}")
                    .log("Circuit Breaker triggered! Service unavailable.")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(503))
                    .setBody(constant(Map.of("error", "service unavailable")))
                    .marshal().json()
                .end();
    }

}
