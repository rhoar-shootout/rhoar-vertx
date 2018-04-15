package com.redhat.labs.adjective.services

import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.impl.ContextImpl
import io.vertx.core.impl.VertxImpl
import io.vertx.core.json.JsonObject
import io.vertx.ext.jdbc.impl.JDBCClientImpl
import io.vertx.ext.sql.SQLConnection
import spock.lang.Specification

class AdjectiveServiceImplTest extends Specification {

    public static final String DB_CONFIG_DATA = '''
                {
                    "db": {
                        "url": "jdbc:h2:file:./adjectives;MODE=PostgreSQL;IGNORECASE=false",
                        "user": "sa",
                        "password": "",
                        "driver_class": "org.h2.Driver"
                    }
                }
            '''

    def "test saveConnHandler happy path"() {
        given: "A valid configuration for the Database Client"
            def configData = new JsonObject(DB_CONFIG_DATA)
        and: "A mock implementation of the Vert.x context"
            def context = GroovyMock(ContextImpl) {
                config() >> configData
            }
        and: "A Mock implementation of the VertxImpl"
            def vertx = GroovySpy(VertxImpl) {
                getOrCreateContext() >> context
            }
        and: "A Mock interceptor for the JDBCClientImpl constructor"
            GroovyMock(JDBCClientImpl, global: true)
        and: "An instance of the class under test"
            def underTest = new AdjectiveServiceImpl(vertx)
        and: "A mock implementation of a handler"
            Handler<AsyncResult<String>> mockHandler = Spy(Handler)
        and: "A Mock implementation of the connection results"
            AsyncResult<SQLConnection> connRes = Spy(AsyncResult)
        and: "A mock implementation of the SQLConnection"
            def conn = Mock(SQLConnection)
        when: "We execute the saveConnHandler method"
            underTest.saveConnHandler('newAdjective', mockHandler, connRes)
        then: "Then we expect the following interactions"
            1 * connRes.succeeded() >> true
            1 * connRes.result() >> conn
            1 * conn.queryWithParams(_, _, _)
    }

    def "test saveConnHandler failed DB client connection"() {
        given: "A valid configuration for the Database Client"
            def configData = new JsonObject(DB_CONFIG_DATA)
        and: "A mock implementation of the Vert.x context"
            def context = GroovyMock(ContextImpl) {
                config() >> configData
            }
        and: "A Mock implementation of the VertxImpl"
            def vertx = GroovySpy(VertxImpl) {
                getOrCreateContext() >> context
            }
        and: "A Mock interceptor for the JDBCClientImpl constructor"
            GroovyMock(JDBCClientImpl, global: true)
        and: "An instance of the class under test"
            def underTest = new AdjectiveServiceImpl(vertx)
        and: "A mock implementation of a handler which expects a failed attempt to get a SQL connection"
            Handler<AsyncResult<String>> mockHandler = { res -> assert res.succeeded() == false }
        and: "A Mock implementation of the connection results"
            AsyncResult<SQLConnection> connRes = Spy(AsyncResult)
        and: "A mock implementation of the SQLConnection"
            def conn = Mock(SQLConnection)
        when: "We execute the saveConnHandler method"
            underTest.saveConnHandler('newAdjective', mockHandler, connRes)
        then: "Then we expect the following interactions"
            1 * connRes.succeeded() >> false        // Return false to indicate that the connection failed
            1 * connRes.cause() >> Mock(Throwable)  // Expect that there is a Throwable in the cause
    }
}
