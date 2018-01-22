package com.redhat.labs.insult

import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClientRequest
import io.vertx.core.json.JsonObject
import org.spockframework.lang.ISpecificationContext
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.AsyncConditions
import com.redhat.labs.noun.MainVerticle as NounVerticle
import com.redhat.labs.adjective.MainVerticle as AdjectiveVerticle

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

        def opts = new DeploymentOptions()
        def nounVerticle = new NounVerticle()
        def adjectiveVerticle = new AdjectiveVerticle()
        def insultVerticle = new MainVerticle()

        vertx.deployVerticle(nounVerticle, opts, { res1 ->
            async.evaluate {
                res1.succeeded()
            }
        })

        vertx.deployVerticle(adjectiveVerticle, opts, { res2 ->
            async.evaluate {
                res2.succeeded()
            }
        })

        vertx.deployVerticle(insultVerticle, opts, { res3 ->
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
        when: "An insult is requested"
            client.getNow(8080, "localhost", "/insult", { res ->
                async.evaluate {
                    res.statusCode() == 200
                    res.bodyHandler({ b ->
                        async.evaluate {
                            def body = b.toJsonObject()
                            body.containsKey("noun")
                            body.containsKey("adj")
                            body.getString("noun").length() > 0
                            body.getJsonArray("adj").size() == 2
                            println(bodyResponse.encodePrettily())
                        }
                    })
                }
            })
        then: "Ensure all async conditions evaluated correctly"
            async.await(10)
    }

    def "Test getting an insult while providing a name"() {
        given: "A Vert.x HTTP client"
            def client = vertx.createHttpClient()
        and: "An instance of AsyncConditions"
            AsyncConditions async = new AsyncConditions(2)
        and: "A POST body of JSON"
            def body = new JsonObject(['name': 'Deven'])
        when: "An insult is requested"
            HttpClientRequest req = client.post(8080, "localhost", "/insult")
            req.handler({ res ->
                async.evaluate {
                    res.statusCode() == 200
                    res.bodyHandler({ b ->
                        async.evaluate {
                            def bodyResponse = b.toJsonObject()
                            bodyResponse.containsKey("noun")
                            bodyResponse.containsKey("adj")
                            bodyResponse.getString("noun").length() > 0
                            bodyResponse.getJsonArray("adj").size() == 2
                            println(bodyResponse.encodePrettily())
                        }
                    })
                }
            })
            req.putHeader("Content-Type", "application/json")
            req.putHeader("Content-Length", "${body.encodePrettily().getBytes("UTF-8").length}")
            req.write(body.encodePrettily())
        then: "Ensure all async conditions evaluated correctly"
            async.await(10)
    }

    def cleanupSpec() {
        AsyncConditions async = new AsyncConditions(1)
        vertx.close({ res ->
            async.evaluate {
                res.succeeded()
            }
        })

        async.await(10)
    }
}
