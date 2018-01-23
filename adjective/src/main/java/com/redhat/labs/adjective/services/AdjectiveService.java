package com.redhat.labs.adjective.services;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

@ProxyGen
@VertxGen
public interface AdjectiveService {

    static AdjectiveService create(Vertx vertx) {
        return new AdjectiveServiceImpl(vertx);
    }

    static AdjectiveService createProxy(Vertx vertx, String address) {
        return new AdjectiveServiceVertxEBProxy(vertx, address);
    }

    void save(String adjective, Handler<AsyncResult<String>> handler);

    void get(Handler<AsyncResult<String>> handler);

    void check(Handler<AsyncResult<String>> handler);
}