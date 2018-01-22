package com.redhat.labs.insult;

import com.redhat.labs.insult.services.InsultService;
import com.redhat.labs.insult.services.InsultServiceImpl;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.*;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.RequestParameter;
import io.vertx.ext.web.api.RequestParameters;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;

import io.vertx.serviceproxy.ServiceBinder;

import static io.netty.handler.codec.http.HttpResponseStatus.*;

public class MainVerticle extends AbstractVerticle {

    InsultService service;

    /**
     * Entry point for {@link MainVerticle}
     * @param startFuture The instance of {@link Future} to be used when the service is completely configured
     */
    @Override
    public void start(final Future<Void> startFuture) {
        // ConfigStore from Kube/OCPs
        Future<JsonObject> f1 = Future.future();
        this.initConfigRetriever(f1.completer());
        f1.compose(this::provisionRouter)
            .compose(this::createHttpServer)
            .compose(s -> startFuture.complete(), startFuture);
    }

    /**
     * Initialize the {@link ConfigRetriever}
     * @param handler Handles the results of requesting the configuration
     */
    private void initConfigRetriever(Handler<AsyncResult<JsonObject>> handler) {
        ConfigStoreOptions defaultOpts = new ConfigStoreOptions()
                .setType("file")
                .setFormat("json")
                .setConfig(new JsonObject().put("path", "default_config.json"));

        ConfigRetrieverOptions retrieverOptions = new ConfigRetrieverOptions()
                .addStore(defaultOpts);

        // Check to see if we are running on Kubernetes/OCP
        if (System.getenv().containsKey("KUBERNETES_NAMESPACE")) {

            ConfigStoreOptions confOpts = new ConfigStoreOptions()
                    .setType("configmap")
                    .setConfig(new JsonObject()
                            .put("name", "insult_config")
                            .put("optional", true)
                    );
            retrieverOptions.addStore(confOpts);
        }

        ConfigRetriever.create(vertx, retrieverOptions).getConfig(handler);
    }

    /**
     * Begin the creation of the {@link OpenAPI3RouterFactory}
     * @param config The config loaded via the {@link ConfigRetriever}
     * @return An {@link OpenAPI3RouterFactory} {@link Future} to be used to complete the next Async step
     */
    private Future<OpenAPI3RouterFactory> provisionRouter(JsonObject config) {
        vertx.getOrCreateContext().config().mergeIn(config);
        service = new InsultServiceImpl(vertx);
        new ServiceBinder(vertx).setAddress("insult.service").register(InsultService.class, service);
        Future<OpenAPI3RouterFactory> future = Future.future();
        CircuitBreaker breaker = CircuitBreaker.create("openApi", vertx, new CircuitBreakerOptions()
                .setMaxFailures(5) // number of failure before opening the circuit
                .setTimeout(200000) // consider a failure if the operation does not succeed in time
                .setFallbackOnFailure(false) // do we call the fallback on failure
                .setResetTimeout(1000000));
        breaker.<OpenAPI3RouterFactory>execute(f -> OpenAPI3RouterFactory.createRouterFactoryFromFile(
                vertx,
                getClass().getResource("/insult.yaml").getFile(),
                f.completer())).setHandler(future.completer());
        return future;
    }

    /**
     * Create an {@link HttpServer} and use the {@link OpenAPI3RouterFactory}
     * to handle HTTP requests
     * @param factory A {@link OpenAPI3RouterFactory} instance which is used to create a {@link Router}
     * @return The {@link HttpServer} instance created
     */
    private Future<HttpServer> createHttpServer(OpenAPI3RouterFactory factory) {
        factory.addHandlerByOperationId("getInsult", this::handleInsult);
        factory.addHandlerByOperationId("insultByName", this::handleNamedInsult);
        factory.addHandlerByOperationId("health", this::healthCheck);
        Future<HttpServer> future = Future.future();
        JsonObject httpJsonCfg = vertx
                .getOrCreateContext()
                .config()
                .getJsonObject("http");
        HttpServerOptions httpConfig = new HttpServerOptions(httpJsonCfg);
        vertx.createHttpServer(httpConfig)
                .requestHandler(factory.getRouter()::accept)
                .listen(future.completer());
        return future;
    }

    private void handleNamedInsult(RoutingContext ctx) {
        RequestParameters params = ctx.get("parsedParameters");
        RequestParameter bodyParam = params.body();
        JsonObject reqBody = bodyParam.getJsonObject();
        String name = reqBody.getString("name");
        if (name!=null && name.length()>0) {
            service.namedInsult(name, result -> {
                if (result.succeeded()) {
                    ctx.response()
                        .setStatusMessage(OK.reasonPhrase())
                        .setStatusCode(OK.code())
                        .end(result.result().encodePrettily());
                } else {
                    ctx.response()
                        .setStatusMessage(INTERNAL_SERVER_ERROR.reasonPhrase())
                        .setStatusCode(INTERNAL_SERVER_ERROR.code())
                        .end();
                }
            });
        }
    }

    private void handleInsult(RoutingContext ctx) {
        service.getInsult(result -> {
            if (result.succeeded()) {
                ctx.response()
                        .setStatusMessage(OK.reasonPhrase())
                        .setStatusCode(OK.code())
                        .end(result.result().encodePrettily());
            } else {
                ctx.response()
                        .setStatusMessage(INTERNAL_SERVER_ERROR.reasonPhrase())
                        .setStatusCode(INTERNAL_SERVER_ERROR.code())
                        .end();
            }
        });
    }

    private void healthCheck(RoutingContext ctx) {
        service.check(res -> {
            if (res.succeeded()) {
                ctx.response()
                        .setStatusCode(CREATED.code())
                        .setStatusMessage(CREATED.reasonPhrase())
                        .end(res.result().encodePrettily());
            } else {
                ctx.response()
                        .setStatusCode(INTERNAL_SERVER_ERROR.code())
                        .setStatusMessage(INTERNAL_SERVER_ERROR.reasonPhrase())
                        .end();
            }
        });
    }
}
