package com.redhat.labs.noun.services;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NounServiceImpl implements NounService {

    private static final Logger LOG = LoggerFactory.getLogger(NounServiceImpl.class);

    SQLClient client;

    /**
     * Default constructor for the Service Implementation
     * @param vertx The {@link Vertx} instance
     */
    public NounServiceImpl(Vertx vertx) {
        JsonObject dbConfig = vertx.getOrCreateContext().config().getJsonObject("db");
        client = JDBCClient.createShared(vertx, dbConfig, "noun");
    }

    /**
     * A method for adding a new Adjective to the database
     * @param adjective The Adjective to be added to the database
     * @param resultHandler The {@link Handler} to be used to callback with the results
     */
    @Override
    public void save(String adjective, Handler<AsyncResult<String>> resultHandler) {
        client.getConnection(connRes -> saveConnHandler(adjective, resultHandler, connRes));
    }

    /**
     * A {@link Handler} method which handles the results of requesting a new database connection
     * @param adjective The Adjective to be saved
     * @param resultHandler The {@link Handler} to be used to callback with the results
     * @param connRes The result of the request for a new database connection instance
     */
    private void saveConnHandler(String adjective, Handler<AsyncResult<String>> resultHandler, AsyncResult<SQLConnection> connRes) {
        if (connRes.succeeded()) {
            SQLConnection conn = connRes.result();
            JsonArray params = new JsonArray().add(adjective);
            conn.queryWithParams("INSERT INTO nouns (noun) VALUES (?)", params, queryRes -> {
                if (queryRes.succeeded()) {
                    JsonObject result = new JsonObject()
                            .put("url", String.format("/%s", adjective));
                    resultHandler.handle(Future.succeededFuture(result.encodePrettily()));
                } else {
                    resultHandler.handle(Future.failedFuture(queryRes.cause()));
                }
            });
        } else {
            resultHandler.handle(Future.failedFuture(connRes.cause()));
        }
    }

    /**
     * A service method to retrieve a random Adjective from the database
     * @param resultHandler The {@link Handler} to be used to callback with the results
     */
    @Override
    public void get(Handler<AsyncResult<String>> resultHandler) {
        client.getConnection(connRes -> handleGetConnectionResult(resultHandler, connRes));
    }

    /**
     * A {@link Handler} method used to handle the request for a new Database connection instance for a GET request
     * @param resultHandler The {@link Handler} to be used to callback with the results
     * @param connRes The result of the request for a new database connection instance
     */
    private void handleGetConnectionResult(Handler<AsyncResult<String>> resultHandler, AsyncResult<SQLConnection> connRes) {
        if (connRes.succeeded()) {
            LOG.debug("DB connection retrieved");
            SQLConnection conn = connRes.result();
            conn.query("SELECT noun FROM nouns ORDER BY RAND() LIMIT 1", queryRes -> {
                LOG.debug("DB Query complete");
                if (queryRes.succeeded()) {
                    LOG.debug("Got noun from DB");
                    ResultSet resultSet = queryRes.result();
                    JsonObject result = resultSet.getRows().get(0);
                    resultHandler.handle(Future.succeededFuture(result.encodePrettily()));
                    connRes.result().close();
                } else {
                    LOG.debug("Failed to get noun from DB", queryRes.cause());
                    resultHandler.handle(Future.failedFuture(queryRes.cause()));
                }
            });
        } else {
            resultHandler.handle(Future.failedFuture(connRes.cause()));
        }
    }

    /**
     * A service method which verifies connectivity to the database for a health check
     * @param resultHandler The {@link Handler} to be used to callback with the results
     */
    public void check(Handler<AsyncResult<String>> resultHandler) {
        client.getConnection(connRes -> {
            if (connRes.succeeded()) {
                connRes.result().query("SELECT 1 FROM nouns LIMIT 1", queryRes -> {
                    if (queryRes.succeeded()) {
                        resultHandler.handle(Future.succeededFuture(new JsonObject()
                                .put("status", "OK")
                                .encodePrettily()));
                        connRes.result().close();
                    } else {
                        resultHandler.handle(Future.failedFuture(queryRes.cause()));
                    }
                });
            } else {
                resultHandler.handle(Future.failedFuture(connRes.cause()));
            }
        });
    }
}
