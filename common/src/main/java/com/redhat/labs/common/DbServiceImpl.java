package com.redhat.labs.common;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;

public class DbServiceImpl implements DbService {

    SQLClient client;

    CircuitBreaker breaker;

    public DbServiceImpl(Vertx vertx) {
        JsonObject jdbcConfig = new JsonObject();
        jdbcConfig.put("url", vertx.getOrCreateContext().config().getValue("db_url"));
        jdbcConfig.put("user", vertx.getOrCreateContext().config().getValue("db_user"));
        jdbcConfig.put("password", vertx.getOrCreateContext().config().getValue("db_pass"));
        jdbcConfig.put("driver_class", vertx.getOrCreateContext().config().getValue("db_driver_class"));
        client = JDBCClient.createShared(vertx, jdbcConfig);
        DbService dbService = DbService.createProxy(vertx, "db.service");

        CircuitBreakerOptions options = new CircuitBreakerOptions()
                .setFallbackOnFailure(true)
                .setMaxRetries(3)
                .setMaxFailures(3)
                .setTimeout(50);

        breaker = CircuitBreaker.create("database", vertx, options);
    }

    @Override
    public void add(String tableName, String value, Handler<AsyncResult> resultHandler) {
        breaker.executeWithFallback(future ->
            client.getConnection(connRes -> {
                if (connRes.succeeded()) {
                    SQLConnection conn = connRes.result();
                    JsonArray params = new JsonArray().add(tableName).add(tableName).add(value);
                    conn.queryWithParams("INSERT INTO ? (?) VALUES (?)", params, resultHandler::handle);
                } else {
                    resultHandler.handle(connRes);
                }
            }), v -> "[database timeout]");
    }

    @Override
    public void get(String tableName, Handler<AsyncResult> resultHandler) {
        breaker.executeWithFallback(future ->
            client.getConnection(connRes -> {
                if (connRes.succeeded()) {
                    SQLConnection conn = connRes.result();
                    JsonArray params = new JsonArray().add(tableName);
                    conn.queryWithParams("SELECT * FROM ? ORDER BY RAND() LIMIT 1", params, resultHandler::handle);
                } else {
                    resultHandler.handle(connRes);
                }
            }), v -> "[database timeout]");
    }
}
