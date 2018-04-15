package com.redhat.labs.adjective.services;

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

/**
 * An implementation of the {@link AdjectiveService} interface.
 * Provides business logic for getting/setting adjectives for use with the Elizabethan Insult Application
 */
public class AdjectiveServiceImpl implements AdjectiveService {

    private static final Logger LOG = LoggerFactory.getLogger(AdjectiveService.class);

    SQLClient client;

    /**
     * Default constructor which takes the {@link Vertx} instance as it's only parameter
     * @param vertx The {@link Vertx} instance for the current context
     */
    public AdjectiveServiceImpl(Vertx vertx) {
        JsonObject dbConfig = vertx.getOrCreateContext().config().getJsonObject("db");
        client = JDBCClient.createShared(vertx, dbConfig, "adjective");
    }

    /* Show how Lambdas are used in a "naive" fashion which can be difficult to read/understand. Nested Lambdas
     * increase cognitive load and decrease testability
    @Override
    public void save(String adjective, Handler<AsyncResult<String>> resultHandler) {
        client.getConnection(connRes -> {       // This is a non-blocking API call, so we have to use a callback handler

            if (connRes.succeeded()) {          // Check to make sure we successfully got a DB connection
                SQLConnection conn = connRes.result();
                JsonArray params = new JsonArray().add(adjective);
                conn.queryWithParams("INSERT INTO adjectives (adjective) VALUES (?)", params, queryRes -> { // Also a non-blocking

                    if (queryRes.succeeded()) {
                        JsonObject result = new JsonObject()
                                .put("url", String.format("/rest/v1/adjective/%s", adjective));
                        resultHandler.handle(Future.succeededFuture(result.encodePrettily()));
                    } else {
                        LOG.error("Failed to successfully execute query to add adjective to the DB", queryRes.cause());
                        resultHandler.handle(Future.failedFuture(queryRes.cause()));
                    }

                });
            } else {
                LOG.error("Failed to get connection to the database", connRes.cause());
                resultHandler.handle(Future.failedFuture(connRes.cause()));
            }

        });
    }
     */

    /**
     * Add a new adjective to the database. This method starts the process of adding a new adjective to the database.
     * The first step is to create a connection to the database.
     * @param adjective The adjective to be added
     * @param resultHandler An instance of {@link Handler} which will be used as a callback upon completion of the operation
     */
    @Override
    public void save(String adjective, Handler<AsyncResult<String>> resultHandler) {
        client.getConnection(connRes -> saveConnHandler(adjective, resultHandler, connRes));
    }

    /**
     * Once we have a connection to the database, we send the query
     * @param adjective The Adjective to be added to the database
     * @param resultHandler The callback to be used once the operation is complete
     * @param connRes The result of the request to open a database connection
     */
    void saveConnHandler(String adjective, Handler<AsyncResult<String>> resultHandler, AsyncResult<SQLConnection> connRes) {
        if (connRes.succeeded()) {
            SQLConnection conn = connRes.result();
            JsonArray params = new JsonArray().add(adjective);
            conn.queryWithParams("INSERT INTO adjectives (adjective) VALUES (?)",
                    params, queryRes -> handleQueryResult(adjective, resultHandler, queryRes));
        } else {
            resultHandler.handle(Future.failedFuture(connRes.cause()));
        }
    }

    /**
     * Once we have a result from the query, we then process that result and use the callback to return the result
     * @param adjective The adjective to be added
     * @param resultHandler The callback to be used once the operation is complete
     * @param queryRes The results of the SQL query
     */
    void handleQueryResult(String adjective, Handler<AsyncResult<String>> resultHandler, AsyncResult<ResultSet> queryRes) {
        if (queryRes.succeeded()) {
            JsonObject result = new JsonObject()
                    .put("url", String.format("/rest/v1/adjective/%s", adjective));
            resultHandler.handle(Future.succeededFuture(result.encodePrettily()));
        } else {
            resultHandler.handle(Future.failedFuture(queryRes.cause()));
        }
    }

    @Override
    public void get(Handler<AsyncResult<String>> resultHandler) {
        client.getConnection(connRes -> handleGetConnectionResult(resultHandler, connRes));
    }

    void handleGetConnectionResult(Handler<AsyncResult<String>> resultHandler, AsyncResult<SQLConnection> connRes) {
        if (connRes.succeeded()) {
            LOG.debug("DB connection retrieved");
            SQLConnection conn = connRes.result();
            conn.query("SELECT adjective FROM adjectives ORDER BY RAND() LIMIT 1", queryRes -> {
                LOG.debug("DB Query complete");
                if (queryRes.succeeded()) {
                    LOG.debug("Got adjective from DB");
                    ResultSet resultSet = queryRes.result();
                    JsonObject result = resultSet.getRows().get(0);
                    resultHandler.handle(Future.succeededFuture(result.encodePrettily()));
                    connRes.result().close();
                } else {
                    LOG.debug("Failed to get adjective from DB");
                    resultHandler.handle(Future.failedFuture(queryRes.cause()));
                }
            });
        } else {
            resultHandler.handle(Future.failedFuture(connRes.cause()));
        }
    }

    public void check(Handler<AsyncResult<String>> handler) {
        client.getConnection(connRes -> {
            if (connRes.succeeded()) {
                connRes.result().query("SELECT 1 FROM adjectives LIMIT 1", queryRes -> {
                    if (queryRes.succeeded()) {
                        handler.handle(Future.succeededFuture(new JsonObject().put("status", "OK").encodePrettily()));
                        connRes.result().close();
                    } else {
                        handler.handle(Future.failedFuture(queryRes.cause()));
                    }
                });
            } else {
                handler.handle(Future.failedFuture(connRes.cause()));
            }
        });
    }
}
