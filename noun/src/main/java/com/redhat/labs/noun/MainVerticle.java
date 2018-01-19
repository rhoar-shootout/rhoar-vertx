package com.redhat.labs.noun;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;

import java.util.function.BiFunction;
import java.util.function.Function;

public class MainVerticle extends AbstractVerticle {

    private void routerFactoryHandler(Future startFuture, AsyncResult<OpenAPI3RouterFactory> ar) {
        if (ar.succeeded()) {
            // Spec loaded with success
            configureApiRoutes(startFuture, ar.result());
        } else {
            // Failed to read API specification from YAML. Refuse to start the service and log the error.
            startFuture.fail(ar.cause());
        }
    }

    @Override
    public void start(final Future<Void> startFuture) throws Exception {
        Handler<AsyncResult<OpenAPI3RouterFactory>> handler = r -> routerFactoryHandler(startFuture, r);
        OpenAPI3RouterFactory
                .createRouterFactoryFromFile(vertx, "/noun.yaml", ar -> handler.handle(ar));
    }

    private void configureApiRoutes(Future startFuture, OpenAPI3RouterFactory routerFactory) {
        routerFactory.addHandlerByOperationId("", NounAPI::getNoun);
        routerFactory.addHandlerByOperationId("", NounAPI::addNoun);
        routerFactory.addHandlerByOperationId("", NounAPI::status);
        routerFactory.addFailureHandlerByOperationId("", NounAPI::handleFailedGet);
        routerFactory.addFailureHandlerByOperationId("", NounAPI::handleFailedPost);
        routerFactory.addFailureHandlerByOperationId("", NounAPI::handleFailedStatus);
        vertx.createHttpServer().requestHandler(routerFactory.getRouter()::accept).listen(8080, startFuture.completer());
    }
}
