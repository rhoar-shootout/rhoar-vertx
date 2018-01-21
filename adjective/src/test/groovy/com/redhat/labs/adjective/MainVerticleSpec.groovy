package com.redhat.labs.adjective

import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import spock.lang.Specification

class MainVerticleSpec extends Specification {

    private Vertx vertx

    def setup() {
        vertx = Vertx.vertx()
        vertx.deployVerticle(MainVerticle.class.getCanonicalName())
    }

    def "Test adjective GET service"() {

    }

    def "Test adjective HEALTH service"() {

    }

    def "Test adjective POST service"() {

    }

    private HttpClient getHttpClient() {
        vertx.createHttpClient();
    }
}
