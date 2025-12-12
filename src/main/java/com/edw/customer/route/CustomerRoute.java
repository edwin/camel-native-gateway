package com.edw.customer.route;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.ThrottlerRejectedExecutionException;

import java.util.Map;

/**
 * <pre>
 *  com.edw.customer.route.CustomerRoute
 * </pre>
 *
 * @author Muhammad Edwin < edwin at redhat dot com >
 * 12 Dec 2025 15:10
 */
@ApplicationScoped
public class CustomerRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // Error Handler for unhandled exceptions
        onException(Exception.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                .setBody(constant(Map.of("error", "gateway error")))
                .marshal().json();

        onException(ThrottlerRejectedExecutionException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(429))
                .setBody(constant(Map.of("error", "too many requests")))
                .marshal().json();

        from("platform-http:/api/v1/customers?matchOnUriPrefix=true")
                .routeId("user-service-gateway")
                .log("Received request : ${header.CamelHttpPath} - body : ${body}")

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
                .removeHeader(Exchange.HTTP_URI)
                .log("firing to downstream service {{downstream.service.url.customer}}")
                .to("http://{{downstream.service.url.customer}}/?bridgeEndpoint=true&throwExceptionOnFailure=true")
                .removeHeaders("Server|X-Powered-By")
                .log("response body is ${body}")

                // handle fallback
                .onFallback()
                    .log("Circuit Breaker triggered! Service unavailable.")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(503))
                    .setBody(constant(Map.of("error", "service unavailable")))
                    .marshal().json()
                .end();
    }

}
