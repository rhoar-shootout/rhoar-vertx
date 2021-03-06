package com.redhat.labs.insult.services;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

@ProxyGen
@VertxGen
public interface InsultService {

    static InsultService create(Vertx vertx) {
        return new InsultServiceImpl(vertx);
    }

    static InsultService createProxy(Vertx vertx, String address) {
        return new InsultServiceVertxEBProxy(vertx, address);
    }

    void getInsult(Handler<AsyncResult<JsonObject>> resultHandler);

    void namedInsult(String name, Handler<AsyncResult<JsonObject>> resultHandler);

    void check(Handler<AsyncResult<JsonObject>> resultHandler);
}
