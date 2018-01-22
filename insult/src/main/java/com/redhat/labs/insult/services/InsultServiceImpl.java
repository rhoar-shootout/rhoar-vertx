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
        nounBreaker = CircuitBreaker.create("noun", vertx, new CircuitBreakerOptions(breakerCfg));
        nounBreaker.openHandler(o -> {
            System.out.println("Noun circuit breaker has opened");
        }).closeHandler(c -> {
            System.out.println("Noun circuit breaker has closed");
        });
        adjBreaker = CircuitBreaker.create("adj", vertx, new CircuitBreakerOptions(breakerCfg));
        adjBreaker.openHandler(o -> {
            System.out.println("Adjective circuit breaker has opened");
        }).closeHandler(c -> {
            System.out.println("Adjective circuit breaker has closed");
        });
    }

    @Override
    public void getInsult(Handler<AsyncResult<JsonObject>> resultHandler) {

        Future<String> nounFuture = Future.future();
        nounBreaker.<String>execute(f -> {
            System.out.println("Executing Noun GET");
            client.getNow(nounCfg.getInteger("port"), nounCfg.getString("host"), "/noun", r -> {
                if (r.statusCode() == OK.code()) {
                    r.bodyHandler(b -> {
                        try {
                            JsonObject body = b.toJsonObject();
                            f.complete("noun:"+body.getString("NOUN"));
                        } catch (Exception e) {
                            f.complete("[noun error]");
                        }
                    });
                } else {
                    f.complete("[noun timeout]");
                }
            });
        }).setHandler(nounFuture.completer());

        Future<String> adj1Future = Future.future();
        adjBreaker.<String>execute(f -> {
            System.out.println("Executing Adj1 GET");
            client.getNow(adjCfg.getInteger("port"), adjCfg.getString("host"), "/adjective", r -> {
                if (r.statusCode() == OK.code()) {
                    r.bodyHandler(b -> {
                        try {
                            JsonObject body = b.toJsonObject();
                            f.complete("adjective:"+body.getString("ADJECTIVE"));
                        } catch (Exception e) {
                            f.complete("[adjective error]");
                        }
                    });
                } else {
                    f.complete("[noun timeout]");
                }
            });
        }).setHandler(adj1Future.completer());

        Future<String> adj2Future = Future.future();
        adjBreaker.<String>execute(f -> {
            System.out.println("Executing Adj2 GET");
            client.getNow(adjCfg.getInteger("port"), adjCfg.getString("host"), "/adjective", r -> {
                if (r.statusCode() == OK.code()) {
                    r.bodyHandler(b -> {
                        try {
                            JsonObject body = b.toJsonObject();
                            f.complete("adjective:"+body.getString("ADJECTIVE"));
                        } catch (Exception e) {
                            f.complete("[adjective error]");
                        }
                    });
                } else {
                    f.complete("[noun timeout]");
                }
            });
        }).setHandler(adj2Future.completer());

        CompositeFuture.all(nounFuture, adj1Future, adj2Future).setHandler(res -> {
            System.out.println("Entered compositeFuture handler");
            if (res.succeeded()) {
                JsonObject response = new JsonObject();
                res.result().list().stream().map(resp -> {
                    String cResp = (String)resp;
                    return cResp;
                })
                .forEach(r -> {
                    System.out.println("Processing: "+r);
                    if (r.startsWith("noun:")) {
                        response.put("noun", r.split(":")[1]);
                    } else if (r.startsWith("adjective:")) {
                        if (!response.containsKey("adj")) {
                            response.put("adj", new JsonArray());
                        }
                        response.getJsonArray("adj").add(r.split(":")[1]);
                    }
                });
                resultHandler.handle(Future.succeededFuture(response));
            } else {
                resultHandler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    @Override
    public void namedInsult(String name, Handler<AsyncResult<JsonObject>> resultHandler) {

        Future<String> nounFuture = Future.future();
        nounBreaker.<String>execute(f -> {
            client.getNow(nounCfg.getInteger("port"), nounCfg.getString("host"), "/noun", r -> {
                if (r.statusCode() == OK.code()) {
                    r.bodyHandler(b -> {
                        try {
                            JsonObject body = b.toJsonObject();
                            f.complete("noun:"+body.getString("NOUN"));
                        } catch (Exception e) {
                            f.complete("[noun error]");
                        }
                    });
                } else {
                    f.complete("[noun timeout]");
                }
            });
        }).setHandler(nounFuture.completer());

        Future<String> adj1Future = Future.future();
        adjBreaker.<String>execute(f -> {
            client.getNow(adjCfg.getInteger("port"), adjCfg.getString("host"), "/adjective", r -> {
                if (r.statusCode() == OK.code()) {
                    r.bodyHandler(b -> {
                        try {
                            JsonObject body = b.toJsonObject();
                            f.complete("adjective:"+body.getString("ADJECTIVE"));
                        } catch (Exception e) {
                            f.complete("[adjective error]");
                        }
                    });
                } else {
                    f.complete("[noun timeout]");
                }
            });
        }).setHandler(adj1Future.completer());

        Future<String> adj2Future = Future.future();
        adjBreaker.<String>execute(f -> {
            client.getNow(adjCfg.getInteger("port"), adjCfg.getString("host"), "/adjective", r -> {
                if (r.statusCode() == OK.code()) {
                    r.bodyHandler(b -> {
                        try {
                            JsonObject body = b.toJsonObject();
                            f.complete("adjective:"+body.getString("ADJECTIVE"));
                        } catch (Exception e) {
                            f.complete("[adjective error]");
                        }
                    });
                } else {
                    f.complete("[noun timeout]");
                }
            });
        }).setHandler(adj2Future.completer());

        CompositeFuture.all(nounFuture, adj1Future, adj2Future).setHandler(res -> {
            if (res.succeeded()) {
                JsonObject response = new JsonObject()
                        .put("subject", name);
                res.result().list().stream().map(resp -> {
                    String cResp = (String)resp;
                    return cResp;
                })
                .forEach(r -> {
                    if (r.startsWith("noun:")) {
                        response.put("noun", r.split(":")[1]);
                    } else if (r.startsWith("adjective:")) {
                        if (!response.containsKey("adj")) {
                            response.put("adj", new JsonArray());
                        }
                        response.getJsonArray("adj").add(r.split(":")[1]);
                    }
                });
                resultHandler.handle(Future.succeededFuture(response));
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
