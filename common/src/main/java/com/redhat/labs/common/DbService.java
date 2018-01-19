package com.redhat.labs.common;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

@ProxyGen
@VertxGen
public interface DbService {

    // A couple of factory methods to create an instance and a proxy
    static DbService create(Vertx vertx) {
        return new DbServiceImpl(vertx);
    }

    static DbService createProxy(Vertx vertx, String address) {
        return new DbServiceVertxEBProxy(vertx, address);
    }

    // Actual service operations here...
    void add(String tableName, String value, Handler<AsyncResult> resultHandler);

    void get(String tableName, Handler<AsyncResult> resultHandler);
}
