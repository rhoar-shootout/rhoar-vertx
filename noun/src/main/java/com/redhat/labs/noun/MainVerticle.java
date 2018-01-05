package com.redhat.labs.noun;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        OpenAPI3RouterFactory.createRouterFactoryFromFile(vertx, "src/main/resources/noun.yaml", ar -> {
            if (ar.succeeded()) {
                // Spec loaded with success
                OpenAPI3RouterFactory routerFactory = ar.result();
            } else {
                // Something went wrong during router factory initialization
                Throwable exception = ar.cause();
            }
        });

    }
}
