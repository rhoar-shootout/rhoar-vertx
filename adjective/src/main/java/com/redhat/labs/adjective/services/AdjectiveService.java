package com.redhat.labs.adjective.services;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * Adjective Service Proxy interface used by Vert.x to generate the EventBus+ServiceProxy abstractions
 */
@ProxyGen
@VertxGen
public interface AdjectiveService {

    /**
     * All service proxies implement {@code create}
     * @param vertx The Vert.x instance
     * @return An instance of the AdjectiveService implementation
     */
    static AdjectiveService create(Vertx vertx) {
        return new AdjectiveServiceImpl(vertx);
    }

    /**
     * All service proxies implement {@code createProxy}
     * @param vertx The Vert.x instance
     * @return An instance of the AdjectiveService implementation
     */
    static AdjectiveService createProxy(Vertx vertx, String address) {
        return new AdjectiveServiceVertxEBProxy(vertx, address);
    }

    /*
    All of the rest of these methods are specific to the service being implemented and represent business logic
     */

    void save(String adjective, Handler<AsyncResult<String>> handler);

    void get(Handler<AsyncResult<String>> handler);

    void check(Handler<AsyncResult<String>> handler);
}