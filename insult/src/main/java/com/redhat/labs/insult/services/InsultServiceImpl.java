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

    public InsultServiceImpl(Vertx vertx) {
        this.vertx = vertx;
        client = vertx.createHttpClient();
        nounCfg = vertx.getOrCreateContext().config().getJsonObject("noun");
        adjCfg = vertx.getOrCreateContext().config().getJsonObject("adjective");
        breakerCfg = vertx.getOrCreateContext().config().getJsonObject("breakers");
    }

    @Override
    public void getInsult(Handler<AsyncResult<JsonObject>> resultHandler) {
        nounBreaker = CircuitBreaker.create("noun", vertx, new CircuitBreakerOptions(breakerCfg));
        adjBreaker = CircuitBreaker.create("adj", vertx, new CircuitBreakerOptions(breakerCfg));

        Future<HttpClientResponse> nounFuture = Future.future();
        nounBreaker.execute(f -> {
            client.getNow(nounCfg.getInteger("port"), nounCfg.getString("host"), "/noun", r -> {
                if (r.statusCode() == OK.code()) {
                    f.complete(r);
                } else {
                    f.complete("[noun timeout]");
                }
            });
        }).setHandler(res -> nounFuture.complete((HttpClientResponse)res));

        Future<HttpClientResponse> adj1Future = Future.future();
        adjBreaker.execute(f -> {
            client.getNow(adjCfg.getInteger("port"), adjCfg.getString("host"), "/adjective", r -> {
                if (r.statusCode() == OK.code()) {
                    f.complete(r);
                } else {
                    f.complete("[adjective timeout]");
                }
            });
        }).setHandler(res -> adj1Future.complete((HttpClientResponse)res));

        Future<HttpClientResponse> adj2Future = Future.future();
        adjBreaker.execute(f -> {
            client.getNow(adjCfg.getInteger("port"), adjCfg.getString("host"), "/adjective", r -> {
                if (r.statusCode() == OK.code()) {
                    f.complete(r);
                } else {
                    f.complete("[adjective timeout]");
                }
            });
        }).setHandler(res -> adj2Future.complete((HttpClientResponse)res));

        CompositeFuture.all(nounFuture, adj1Future, adj2Future).setHandler(res -> {
            if (res.succeeded()) {
                JsonObject response = new JsonObject();
                res.result().list().stream().map(resp -> {
                    HttpClientResponse cResp = (HttpClientResponse)resp;
                    return cResp;
                })
                .filter(r -> r.statusCode()==200)
                .forEach(r -> {
                    r.bodyHandler(b -> {
                        try {
                            JsonObject responseObj = b.toJsonObject();
                            if (responseObj.containsKey("noun") || responseObj.containsKey("NOUN")) {
                                response.put("noun", responseObj.getString("noun"));
                            } else if (responseObj.containsKey("ADJECTIVE") || responseObj.containsKey("adjective")) {
                                if (!response.containsKey("adj")) {
                                    response.put("adj", new JsonArray());
                                }
                                response.getJsonArray("adj").add(responseObj.getString("adjective"));
                            }
                        } catch (Exception e) {
                            // Ignore this exception
                        }
                    });
                });
            } else {
                resultHandler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    @Override
    public void namedInsult(String name, Handler<AsyncResult<JsonObject>> resultHandler) {

        Future<HttpClientResponse> nounFuture = Future.future();
        nounBreaker.execute(f -> {
            client.getNow(nounCfg.getInteger("port"), nounCfg.getString("host"), "/noun", r -> {
                if (r.statusCode() == OK.code()) {
                    f.complete(r);
                } else {
                    f.complete("[noun timeout]");
                }
            });
        }).setHandler(res -> nounFuture.complete((HttpClientResponse)res));

        Future<HttpClientResponse> adj1Future = Future.future();
        adjBreaker.execute(f -> {
            client.getNow(adjCfg.getInteger("port"), adjCfg.getString("host"), "/adjective", r -> {
                if (r.statusCode() == OK.code()) {
                    f.complete(r);
                } else {
                    f.complete("[adjective timeout]");
                }
            });
        }).setHandler(res -> adj1Future.complete((HttpClientResponse)res));

        Future<HttpClientResponse> adj2Future = Future.future();
        adjBreaker.execute(f -> {
            client.getNow(adjCfg.getInteger("port"), adjCfg.getString("host"), "/adjective", r -> {
                if (r.statusCode() == OK.code()) {
                    f.complete(r);
                } else {
                    f.complete("[adjective timeout]");
                }
            });
        }).setHandler(res -> adj2Future.complete((HttpClientResponse)res));

        CompositeFuture.all(nounFuture, adj1Future, adj2Future).setHandler(res -> {
            if (res.succeeded()) {
                JsonObject response = new JsonObject().put("subject", name);
                res.result().list().stream().map(resp -> {
                    HttpClientResponse cResp = (HttpClientResponse)resp;
                    return cResp;
                })
                        .filter(r -> r.statusCode()==200)
                        .forEach(r -> {
                            r.bodyHandler(b -> {
                                try {
                                    JsonObject responseObj = b.toJsonObject();
                                    if (responseObj.containsKey("noun") || responseObj.containsKey("NOUN")) {
                                        response.put("noun", responseObj.getString("noun"));
                                    } else if (responseObj.containsKey("ADJECTIVE") || responseObj.containsKey("adjective")) {
                                        if (!response.containsKey("adj")) {
                                            response.put("adj", new JsonArray());
                                        }
                                        response.getJsonArray("adj").add(responseObj.getString("adjective"));
                                    }
                                } catch (Exception e) {
                                    // Ignore this exception
                                }
                            });
                        });
            } else {
                resultHandler.handle(Future.failedFuture(res.cause()));
            }
        });

    }

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
