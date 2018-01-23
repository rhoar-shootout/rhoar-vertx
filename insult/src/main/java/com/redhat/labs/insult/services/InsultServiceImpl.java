package com.redhat.labs.insult.services;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.core.*;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;

public class InsultServiceImpl implements InsultService {

    Vertx vertx;
    HttpClient client;
    JsonObject nounCfg, adjCfg, breakerCfg;
    private CircuitBreaker nounBreaker;
    private CircuitBreaker adjBreaker;

    /**
     * Default constructor. Sets up the circuit breakers and other requirements for this Insult service
     * @param vertx The {@link Vertx} instance
     */
    public InsultServiceImpl(Vertx vertx) {
        this.vertx = vertx;
        client = vertx.createHttpClient();
        nounCfg = vertx.getOrCreateContext().config().getJsonObject("noun");
        adjCfg = vertx.getOrCreateContext().config().getJsonObject("adjective");
        breakerCfg = vertx.getOrCreateContext().config().getJsonObject("breakers");
        nounBreaker = CircuitBreaker.create("noun", vertx, new CircuitBreakerOptions(breakerCfg));
        nounBreaker.openHandler(o -> System.out.println("Noun circuit breaker has opened"))
                .closeHandler(c -> System.out.println("Noun circuit breaker has closed"))
                .fallback(t -> "[noun fallback]");
        adjBreaker = CircuitBreaker.create("adj", vertx, new CircuitBreakerOptions(breakerCfg));
        adjBreaker.openHandler(o -> System.out.println("Adjective circuit breaker has opened"))
                .closeHandler(c -> System.out.println("Adjective circuit breaker has closed"))
                .fallback(t -> "[adjective fallback]");
    }

    /**
     * Retrieve an insult result JSON
     * @param resultHandler The {@link Handler} to be called with the results of the request.
     */
    @Override
    public void getInsult(Handler<AsyncResult<JsonObject>> resultHandler) {
        namedInsult(null, resultHandler);
    }

    /**
     * A method for making a REST API call against the other microservices
     * @param cfg The {@link JsonObject} for configuring the Async HTTP Client
     * @param path The path for the request
     * @param f A {@link Future} which will be used to handle the results
     */
    private void makeRestCall(JsonObject cfg, String path, Future<String> f) {
        System.out.println("Executing GET: "+path);
        client.getNow(cfg.getInteger("port"), cfg.getString("host"), path, r -> {
            if (r.statusCode() == OK.code()) {
                r.bodyHandler(b -> {
                    try {
                        JsonObject body = b.toJsonObject();
                        if (body.containsKey("NOUN")) {
                            f.complete(body.getString("NOUN"));
                        } else {
                            f.complete(body.getString("ADJECTIVE"));
                        }
                    } catch (Exception e) {
                        f.complete("[error]");
                    }
                });
            } else {
                f.complete("[timeout]");
            }
        });
    }

    /**
     * A service method to generating an insult for a specific name
     * @param name The name to direct the insult at
     * @param resultHandler The {@link Handler} to be used to call back to the calling Verticle
     */
    @Override
    public void namedInsult(String name, Handler<AsyncResult<JsonObject>> resultHandler) {

        Future<String> nounFuture = Future.future();
        nounBreaker.<String>execute(f -> makeRestCall(nounCfg, "/noun", f)).setHandler(nounFuture.completer());

        Future<String> adj1Future = Future.future();
        adjBreaker.<String>execute(f -> makeRestCall(adjCfg, "/adjective", f)).setHandler(adj1Future.completer());

        Future<String> adj2Future = Future.future();
        adjBreaker.<String>execute(f -> makeRestCall(adjCfg, "/adjective", f)).setHandler(adj2Future.completer());

        CompositeFuture.join(nounFuture, adj1Future, adj2Future).setHandler(res -> {
            JsonObject response = new JsonObject();
            if (name != null) {
                response.put("subject", name);
            }
            response.put("noun", nounFuture.result()==null?"[noun timeout]":nounFuture.result());
            response.put("adj1", adj1Future.result()==null?"[adjective timeout]":adj1Future.result());
            response.put("adj2", adj2Future.result()==null?"[adjective timeout]":adj2Future.result());
            resultHandler.handle(Future.succeededFuture(response));
        });
    }

    /**
     * A service method or health check
     * @param handler The {@link Handler} to be used to call back to the calling Verticle
     */
    public void check(Handler<AsyncResult<JsonObject>> handler) {
        boolean allBreakersClosed = (
                (nounBreaker.state().equals(CircuitBreakerState.CLOSED)) &&
                (adjBreaker.state().equals(CircuitBreakerState.CLOSED))
        );
        JsonObject health = new JsonObject()
                .put("noun", new JsonObject()
                                    .put("failures", nounBreaker.failureCount())
                                    .put("state", nounBreaker.state().toString()))
                .put("adjective", new JsonObject()
                                    .put("failures", adjBreaker.failureCount())
                                    .put("state", adjBreaker.state().toString()))
                .put("status", allBreakersClosed?"OK":"UNHEALTHY");
        if (allBreakersClosed) {
            handler.handle(Future.succeededFuture(health));
        } else {
            handler.handle(Future.failedFuture(health.encodePrettily()));
        }
    }
}
