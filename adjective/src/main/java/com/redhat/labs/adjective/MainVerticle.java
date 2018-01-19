package com.redhat.labs.adjective;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;

public class MainVerticle extends AbstractVerticle {

    private static void routerFactoryHandler(Future startFuture, AsyncResult<OpenAPI3RouterFactory> ar) {
        if (ar.succeeded()) {
            // Spec loaded with success
            OpenAPI3RouterFactory routerFactory = ar.result();
        } else {
            // Something went wrong during router factory initialization
            startFuture.fail(ar.cause());
        }
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        Handler<AsyncResult<OpenAPI3RouterFactory>> apiFactoryHandler = factory -> MainVerticle.routerFactoryHandler(startFuture, factory);
        OpenAPI3RouterFactory.createRouterFactoryFromFile(vertx, "src/main/resources/adjective.yaml", apiFactoryHandler::handle);
    }
}
