package com.redhat.labs.insult

import com.redhat.labs.insult.services.InsultService
import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClientRequest
import io.vertx.core.http.RequestOptions
import io.vertx.core.json.JsonObject
import org.spockframework.lang.ISpecificationContext
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.AsyncConditions

import static io.netty.handler.codec.http.HttpResponseStatus.OK

class MainVerticleSpec extends Specification {

    @Shared
    Vertx vertx

    @Override
    ISpecificationContext getSpecificationContext() {
        return super.getSpecificationContext()
    }

    def setupSpec() {
        AsyncConditions async = new AsyncConditions(3)

        vertx = Vertx.vertx()

        // Fake Adjective Service
        vertx.deployVerticle(new AbstractVerticle() {
            /**
             * If your verticle does a simple, synchronous start-up then override this method and put your start-up
             * code in here.
             * @throws Exception
             */
            @Override
            void start(Future<Void> startFuture) throws Exception {
                vertx.createHttpServer().requestHandler({ req1 ->
                    def response = new JsonObject()
                            .put("ADJECTIVE", "testadjective")
                            .encodePrettily()
                    req1.response()
                            .setStatusCode(OK.code())
                            .setStatusMessage(OK.reasonPhrase())
                            .end(response)
                }).listen(8082, "0.0.0.0", startFuture.completer())
            }
        }, { res1 ->
            async.evaluate {
                res1.cause() == null
            }
        })

        // Fake Noun Service
        vertx.deployVerticle(new AbstractVerticle() {
            /**
             * If your verticle does a simple, synchronous start-up then override this method and put your start-up
             * code in here.
             * @throws Exception
             */
            @Override
            void start(Future<Void> startFuture) throws Exception {
                vertx.createHttpServer().requestHandler({ req2 ->
                    def response = new JsonObject()
                            .put("NOUN", "testnoun")
                            .encodePrettily()
                    req2.response()
                            .setStatusCode(OK.code())
                            .setStatusMessage(OK.reasonPhrase())
                            .end(response)
                }).listen(8083, "0.0.0.0", startFuture.completer())
            }
        }, { res2 ->
            async.evaluate {
                res2.cause() == null
            }
        })

        def opts = new DeploymentOptions()

        vertx.deployVerticle(new MainVerticle(), opts, { res3 ->
            async.evaluate {
                res3.succeeded()
            }
        })

        async.await(20)
    }

    def "Test getting an insult without a specified name"() {
        given: "A Vert.x HTTP client"
            def client = vertx.createHttpClient()
        and: "An instance of AsyncConditions"
            AsyncConditions async = new AsyncConditions(2)
        and: "A request with the correct headers"
            def req = client.get(8080, "localhost", "/api/v1/insult")
            req.putHeader("Origin", "http://localhost:8081")
        when: "An insult is requested"
            req.handler({ res ->
                async.evaluate {
                    res.statusCode() == 200
                    res.getHeader("Access-Control-Allow-Origin") == '*'
                    res.bodyHandler({  b ->
                        async.evaluate {
                            def body = b.toJsonObject()
                            body.containsKey("noun")
                            body.containsKey("adj1")
                            body.containsKey("adj2")
                            body.getString("noun") == "testnoun"
                            body.getString("adj1") == "testadjective"
                            body.getString("adj2") == "testadjective"
                            println(body.encodePrettily())
                        }
                    })
                }
            }).end()
        then: "Ensure all async conditions evaluated correctly"
            async.await(20)
    }

    def "Test getting an insult while providing a name"() {
        given: "A Vert.x HTTP client"
            def client = vertx.createHttpClient()
        and: "An instance of AsyncConditions"
            AsyncConditions async = new AsyncConditions(2)
        and: "A POST body of JSON"
            def body = new JsonObject(['name': 'Deven'])
        and: "A request with the correct headers"
            def req = client.post(8080, "localhost", "/api/v1/insult")
            req.putHeader("Origin", "http://localhost:8081")
            req.putHeader("Content-Type", "application/json")
            req.putHeader("Content-Length", "${body.encodePrettily().getBytes("UTF-8").length}")
        when: "An insult is requested"
            req.handler({ res ->
                async.evaluate {
                    res.statusCode() == 201
                    res.getHeader("Access-Control-Allow-Origin") == '*'
                    res.bodyHandler({ b ->
                        async.evaluate {
                            def responseBody = b.toJsonObject()
                            responseBody.containsKey("noun")
                            responseBody.containsKey("adj1")
                            responseBody.containsKey("adj2")
                            responseBody.getString("noun") == "testnoun"
                            responseBody.getString("adj1") == "testadjective"
                            responseBody.getString("adj2") == "testadjective"
                            println(responseBody.encodePrettily())
                        }
                    })
                }
            }).end(body.encodePrettily())
        then: "Ensure all async conditions evaluated correctly"
            async.await(20)
    }

    def "Test getting an insult without a name via Service Proxy"() {
        given: "A Vert.x HTTP client"
            def client = vertx.createHttpClient()
        and: "An instance of AsyncConditions"
            AsyncConditions async = new AsyncConditions(1)
        when: "An insult is requested"
            InsultService.createProxy(vertx, "insult.service").getInsult({ res ->
                async.evaluate {
                    res.succeeded()
                    res.result() instanceof JsonObject
                    JsonObject body = res.result()
                    body.containsKey("noun")
                    body.containsKey("adj1")
                    body.containsKey("adj2")
                    body.getString("noun") == "testnoun"
                    body.getString("adj1") == "testadjective"
                    body.getString("adj2") == "testadjective"
                    println(body.encodePrettily())
                }
            })
        then: "Ensure all async conditions evaluated correctly"
            async.await(20)
    }

    def "Test getting an insult with a name via Service Proxy"() {
        given: "A Vert.x HTTP client"
            def client = vertx.createHttpClient()
        and: "An instance of AsyncConditions"
            AsyncConditions async = new AsyncConditions(1)
        when: "An insult is requested"
            InsultService.createProxy(vertx, "insult.service").namedInsult("Deven", { res ->
                async.evaluate {
                    res.succeeded()
                    res.result() instanceof JsonObject
                    JsonObject body = res.result()
                    body.containsKey("noun")
                    body.containsKey("adj1")
                    body.containsKey("adj2")
                    body.getString("noun") == "testnoun"
                    body.getString("adj1") == "testadjective"
                    body.getString("adj2") == "testadjective"
                    body.getString("subject") == "Deven"
                    println(body.encodePrettily())
                }
            })
        then: "Ensure all async conditions evaluated correctly"
            async.await(20)
    }

    def cleanupSpec() {
        AsyncConditions async = new AsyncConditions(1)

        vertx.close({ res ->
            async.evaluate {
                res.succeeded()
            }
        })

        async.await(20)
    }
}
