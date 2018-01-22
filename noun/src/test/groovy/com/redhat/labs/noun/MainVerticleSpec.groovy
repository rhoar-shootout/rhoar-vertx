package com.redhat.labs.noun

import io.vertx.core.Vertx
import io.vertx.core.http.HttpClientOptions
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.AsyncConditions

class MainVerticleSpec extends Specification {

    @Shared
    private Vertx vertx

    def setupSpec() {
        AsyncConditions async = new AsyncConditions(1)
        vertx = Vertx.vertx()
        vertx.deployVerticle(MainVerticle.class.getCanonicalName(), { res ->
            async.evaluate {
                res.succeeded()
            }
        })

        async.await(10)
    }

    def "Test adjective GET service"() {
        given: "A Vert.x HTTP Client"
            def client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(8080))
        and: "An instance of AsyncConditions"
            def async = new AsyncConditions(2)

        when: "An HTTP request is made to the noun service"
            client.getNow("/noun", { res ->
                async.evaluate {
                    res.statusCode() == 200
                    res.bodyHandler({ bodyRes ->
                        async.evaluate {
                            !bodyRes.toJsonObject().getString("NOUN").isEmpty()
                        }
                    })
                }
            })

        then: "Expect async conditions to evaluate correctly"
            async.await(10.0)
    }

    def "Test adjective HEALTH service"() {
        given: "A Vert.x HTTP Client"
        def client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(8080))
        and: "An instance of AsyncConditions"
        def async = new AsyncConditions(2)

        when: "An HTTP request is made to the noun service"
        client.getNow("/noun", { res ->
            async.evaluate {
                res.statusCode() == 200
                res.bodyHandler({ bodyRes ->
                    async.evaluate {
                        bodyRes.toJsonObject().getString("STATUS") == "OK"
                    }
                })
            }
        })

        then: "Expect async conditions to evaluate correctly"
        async.await(10.0)
    }
}
