package com.redhat.labs.insult;

import com.redhat.labs.common.DbServiceImpl;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;

import java.util.Arrays;
import java.util.Map;
import com.redhat.labs.common.DbService;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions;
import io.vertx.serviceproxy.ServiceBinder;

import static io.netty.handler.codec.http.HttpHeaders.Values.APPLICATION_JSON;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.vertx.core.http.HttpMethod.POST;

public class MainVerticle extends AbstractVerticle {

    private DbService service;

    /**
     * Handle results of {@link OpenAPI3RouterFactory} creation
     * @param startFuture The instance of {@link Future} to be used when the service is completely configured
     * @param ar The {@link AsyncResult} of creating the {@link OpenAPI3RouterFactory}
     */
    private void routerFactoryHandler(Future startFuture, AsyncResult<OpenAPI3RouterFactory> ar) {
        if (ar.succeeded()) {
            // Spec loaded with success
            configureApiRoutes(startFuture, ar.result());
        } else {
            // Failed to read API specification from YAML. Refuse to start the service and log the error.
            startFuture.fail(ar.cause());
        }
    }

    /**
     * Entry point for {@link MainVerticle}
     * @param startFuture The instance of {@link Future} to be used when the service is completely configured
     */
    @Override
    public void start(final Future<Void> startFuture) {
        service = new DbServiceImpl(vertx);

        // Bind the database service
        new ServiceBinder(vertx)
                .setAddress("db.service")
                .register(DbService.class, service);

        Handler<AsyncResult<OpenAPI3RouterFactory>> handler = r -> routerFactoryHandler(startFuture, r);
        OpenAPI3RouterFactory
                .createRouterFactoryFromFile(vertx, "/insult.yaml", handler::handle);
    }

    /**
     * Configures the {@link OpenAPI3RouterFactory} once successfully created
     * @param startFuture The instance of {@link Future} to be used when the service is completely configured
     * @param routerFactory The newly created instance of {@link OpenAPI3RouterFactory}
     */
    private void configureApiRoutes(Future startFuture, OpenAPI3RouterFactory routerFactory) {
        routerFactory.addHandlerByOperationId("getInsult", InsultAPI::getInsult);
        routerFactory.addHandlerByOperationId("health", InsultAPI::status);
        routerFactory.addFailureHandlerByOperationId("getInsult", InsultAPI::handleFailedGet);
        routerFactory.addFailureHandlerByOperationId("health", InsultAPI::handleFailedStatus);

        Router router = routerFactory.getRouter();

        // Configure SockJS EventBus Bridge
        BridgeOptions bridgeOpts = new BridgeOptions();
        bridgeOpts
                .setMaxAddressLength(40)
                .setPingTimeout(10)
                .setOutboundPermitted(Arrays.asList(
                        new PermittedOptions().setAddress("db.service"))
                );
        bridgeOpts
                .setInboundPermitted(Arrays.asList(
                        new PermittedOptions().setAddress("db.service"))
                );

        router.route("/eventbus").handler(SockJSHandler.create(vertx).bridge(bridgeOpts));
        vertx.createHttpServer().requestHandler(router::accept).listen(8080, startFuture.completer());
    }

    /**
     * A method which handles requests via the Database Service Proxy
     * @param ctx The {@link RoutingContext} of the request
     */
    private void handleRequest(RoutingContext ctx) {
        switch(ctx.request().path()) {
            case "/insult":
                buildInsult(ctx);
                break;
            case "/health":
                ctx.response()
                        .setStatusCode(OK.code())
                        .setStatusMessage(OK.reasonPhrase())
                        .end(new JsonObject()
                                .put("status", "OK")
                                .encodePrettily());
        }
    }

    private void buildInsult(RoutingContext ctx) {
        HttpClient client = vertx.createHttpClient();

        CircuitBreakerOptions options = new CircuitBreakerOptions()
                .setMaxFailures(3)
                .setMaxRetries(3)
                .setTimeout(50)
                .setFallbackOnFailure(true);

        CircuitBreaker breaker = CircuitBreaker.create("adjective", vertx, options);

        Future<String> adjective = breaker.executeWithFallback(future -> {
            // Try the actual client request
            client.getNow()
        }, v -> {
            // If the circuit breaker is open, return a default value
            return "[service timeout]";
        });
    }
}
