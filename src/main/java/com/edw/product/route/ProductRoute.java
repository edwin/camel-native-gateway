package com.edw.product.route;

import com.edw.common.route.BaseRoute;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;

/**
 * <pre>
 *  com.edw.product.route.ProductRoute
 * </pre>
 *
 * @author Muhammad Edwin < edwin at redhat dot com >
 * 12 Dec 2025 16:48
 */
@ApplicationScoped
public class ProductRoute extends BaseRoute {

    @Override
    protected String getRouteUrl() {
        return "platform-http:/api/v1/products?matchOnUriPrefix=true";
    }

    @Override
    protected String getRouteId() {
        return "product-service-gateway";
    }

    @Override
    protected String getDownstreamUrl() {
        return "{{downstream.service.url.product}}/?bridgeEndpoint=true&throwExceptionOnFailure=true&connectTimeout={{downstream.http.connectTimeout}}" +
                "&socketTimeout={{downstream.http.socketTimeout}}";
    }

    @Override
    protected String getDownstreamLogMessage() {
        return "firing to downstream service {{downstream.service.url.product}}${header.CamelHttpPath}?${header.CamelHttpQuery}";
    }

    @Override
    protected void prepareDownstreamRequest(Exchange exchange) {
        // changing from /api/v1/products to /api/product
        String path = exchange.getIn().getHeader(Exchange.HTTP_PATH, String.class);
        if (path != null) {
            exchange.getIn().setHeader(Exchange.HTTP_PATH, path.replace("/v1/products", "/product"));
        }
    }
}
