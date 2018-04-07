package com.redhat.labs.adjective

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

        async.await(25)
    }

    def "Test adjective GET service"() {
        given: "A Vert.x HTTP Client"
            def client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(8082))
        and: "An instance of AsyncConditions"
            def async = new AsyncConditions(2)

        when: "An HTTP request is made to the adjective service"
            client.getNow("/api/v1/adjective", { res ->
                async.evaluate {
                    res.statusCode() == 200
                    res.bodyHandler({ bodyRes ->
                        async.evaluate {
                            !bodyRes.toJsonObject().getString("ADJECTIVE").isEmpty()
                        }
                    })
                }
            })

        then: "Expect async conditions to evaluate correctly"
            async.await(10.0)
    }

    def "Test adjective HEALTH service"() {
        given: "A Vert.x HTTP Client"
            def client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(8082))
        and: "An instance of AsyncConditions"
            def async = new AsyncConditions(2)

        when: "An HTTP request is made to the adjective service"
            client.getNow("/api/v1/health", { res ->
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

    def cleanupSpec() {
        AsyncConditions async = new AsyncConditions(1)

        vertx.close({ res ->
            async.evaluate {
                res.succeeded()
            }
        })

        async.await(5)
    }
}
