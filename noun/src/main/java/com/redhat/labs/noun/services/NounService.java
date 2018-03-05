package com.redhat.labs.noun.services;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

@ProxyGen
@VertxGen
public interface NounService {

    static NounService create(Vertx vertx) {
        return new NounServiceImpl(vertx);
    }

    static NounService createProxy(Vertx vertx, String address) {
        return new NounServiceVertxEBProxy(vertx, address);
    }

    void save(String noun, Handler<AsyncResult<String>> handler);

    void get(Handler<AsyncResult<String>> handler);

    void check(Handler<AsyncResult<String>> handler);
}