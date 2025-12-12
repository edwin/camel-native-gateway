package com.edw.customer.route;

import com.edw.common.route.BaseRoute;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * <pre>
 *  com.edw.customer.route.CustomerRoute
 * </pre>
 *
 * @author Muhammad Edwin < edwin at redhat dot com >
 * 12 Dec 2025 15:10
 */
@ApplicationScoped
public class CustomerRoute extends BaseRoute {

    @Override
    protected String getRouteUrl() {
        return "platform-http:/api/v1/customers?matchOnUriPrefix=true";
    }

    @Override
    protected String getRouteId() {
        return "user-service-gateway";
    }

    @Override
    protected String getDownstreamUrl() {
        return "{{downstream.service.url.customer}}/?bridgeEndpoint=true&throwExceptionOnFailure=true&connectTimeout={{downstream.http.connectTimeout}}" +
                "&socketTimeout={{downstream.http.socketTimeout}}";
    }

    @Override
    protected String getDownstreamLogMessage() {
        return "firing to downstream service {{downstream.service.url.customer}}";
    }
}
