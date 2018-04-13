package com.redhat.labs.insult;

import com.redhat.labs.insult.services.InsultService;
import com.redhat.labs.insult.services.InsultServiceImpl;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.*;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.RequestParameter;
import io.vertx.ext.web.api.RequestParameters;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;

import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.serviceproxy.ServiceBinder;

import java.util.Arrays;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.vertx.core.http.HttpMethod.*;

public class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    private static final String INSULT_SERVICE = "insult.service";

    private InsultService service;

    /**
     * Entry point for {@link MainVerticle}
     * @param startFuture The instance of {@link Future} to be used when the service is completely configured
     */
    @Override
    public void start(final Future<Void> startFuture) {
        // ConfigStore from Kube/OCPs
        this.initConfigRetriever()
            .compose(this::provisionRouter)
            .compose(this::createHttpServer)
            .compose(s -> startFuture.complete(), startFuture);
    }

    /**
     * Initialize the {@link ConfigRetriever} and return a {@link Future}
     * @return A {@link Future} which resolves with the loaded configuration as a {@link JsonObject}
     */
    private Future<JsonObject> initConfigRetriever() {
        Future<JsonObject> configFuture = Future.future();
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
                            .put("name", "insult-config")
                            .put("optional", true)
                    );
            retrieverOptions.addStore(confOpts);
        }

        ConfigRetriever
                .create(vertx, retrieverOptions)
                .getConfig(configFuture.completer());
        return configFuture;
    }

    /**
     * Begin the creation of the {@link OpenAPI3RouterFactory}
     * @param config The config loaded via the {@link ConfigRetriever}
     * @return An {@link OpenAPI3RouterFactory} {@link Future} to be used to complete the next Async step
     */
    private Future<OpenAPI3RouterFactory> provisionRouter(JsonObject config) {
        vertx.getOrCreateContext().config().mergeIn(config);
        LOG.info(vertx.getOrCreateContext().config().encodePrettily());
        service = new InsultServiceImpl(vertx);
        new ServiceBinder(vertx).setAddress(INSULT_SERVICE).register(InsultService.class, service);
        Future<OpenAPI3RouterFactory> future = Future.future();
        CircuitBreaker breaker = CircuitBreaker.create("openApi", vertx, new CircuitBreakerOptions()
                .setMaxFailures(5) // number of failure before opening the circuit
                .setTimeout(200000) // consider a failure if the operation does not succeed in time
                .setFallbackOnFailure(false) // do we call the fallback on failure
                .setResetTimeout(1000000));
        breaker.<OpenAPI3RouterFactory>execute(f -> OpenAPI3RouterFactory.create(
                vertx,
                "/insult.yaml",
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
        Router baseRouter = Router.router(vertx);
        baseRouter.route().handler(ctx -> {
            LOG.info(ctx.request().path());
            ctx.next();
        });
        @Nullable JsonObject config = vertx.getOrCreateContext().config();
        CorsHandler corsHandler = CorsHandler.create("*")
                .allowedHeader("Access-Control-Request-Method")
                .allowedHeader("Access-Control-Allow-Origin")
                .allowedHeader("Access-Control-Allow-Headers")
                .allowedHeader("Content-Type")
                .allowedMethod(GET)
                .allowedMethod(POST)
                .allowedMethod(HEAD)
                .allowedMethod(PUT)
                .allowedMethod(OPTIONS)
                .allowedMethod(CONNECT);
        baseRouter.route().handler(corsHandler);
        factory.addHandlerByOperationId("getInsult", ctx -> service.getInsult(result -> handleResponse(ctx, OK, result)));
        factory.addHandlerByOperationId("insultByName", this::handleNamedInsult);
        factory.addHandlerByOperationId("health", ctx -> service.check(res -> handleResponse(ctx, OK, res)));
        Future<HttpServer> future = Future.future();
        JsonObject httpJsonCfg = config
                .getJsonObject("http");
        HttpServerOptions httpConfig = new HttpServerOptions(httpJsonCfg);
        Router router = factory.getRouter();
        BridgeOptions bOpts = new BridgeOptions();
        bOpts.setInboundPermitted(Arrays.asList(new PermittedOptions().setAddress(INSULT_SERVICE)));
        bOpts.setOutboundPermitted(Arrays.asList(new PermittedOptions().setAddress(INSULT_SERVICE)));
        SockJSHandler sockHandler = SockJSHandler.create(vertx).bridge(bOpts);
        baseRouter.route("/eventbus/*").handler(sockHandler);
        baseRouter.mountSubRouter("/api/v1", router);
        vertx.createHttpServer(httpConfig)
                .requestHandler(baseRouter::accept)
                .listen(future.completer());
        return future;
    }

    /**
     * Handle a request for a named insult
     * @param ctx The {@link RoutingContext} for the request, from which we will extract the parameters
     */
    private void handleNamedInsult(RoutingContext ctx) {
        RequestParameters params = ctx.get("parsedParameters");
        RequestParameter bodyParam = params.body();
        JsonObject reqBody = bodyParam.getJsonObject();
        String name = reqBody.getString("name");
        if (name!=null && name.length()>0) {
            service.namedInsult(name, result -> handleResponse(ctx, CREATED, result));
        } else {
            service.getInsult(result -> handleResponse(ctx, CREATED, result));
        }
    }

    /**
     * Handles a Service Proxy response and uses the {@link RoutingContext} to send the response
     * @param ctx The {@link RoutingContext} of the request we are responding to
     * @param status The {@link HttpResponseStatus} to be used for the request's successful response
     * @param res The {@link AsyncResult} which contains a JSON body for the response or an exception
     */
    private void handleResponse(RoutingContext ctx, HttpResponseStatus status, AsyncResult<JsonObject> res) {
        if (res.succeeded()) {
            ctx.response()
                    .setStatusCode(status.code())
                    .setStatusMessage(status.reasonPhrase())
                    .end(res.result().encodePrettily());
        } else {
            ctx.response()
                    .setStatusCode(INTERNAL_SERVER_ERROR.code())
                    .setStatusMessage(INTERNAL_SERVER_ERROR.reasonPhrase())
                    .end();
        }
    }
}
