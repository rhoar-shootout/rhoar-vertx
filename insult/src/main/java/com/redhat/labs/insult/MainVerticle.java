package com.redhat.labs.insult;

import com.redhat.labs.insult.services.InsultService;
import com.redhat.labs.insult.services.InsultServiceImpl;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.Maybe;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.api.RequestParameter;
import io.vertx.ext.web.api.RequestParameters;

import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;
import io.vertx.reactivex.ext.web.handler.CorsHandler;
import io.vertx.reactivex.ext.web.handler.sockjs.SockJSHandler;
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
        this.initConfigRetriever()
            .flatMap(this::provisionRouter)
            .flatMap(this::createHttpServer)
            .doOnError(startFuture::fail)
            .subscribe(v -> startFuture.complete());
    }

    /**
     * Initialize the {@link ConfigRetriever} and return a {@link Maybe}
     * @return A {@link Maybe} which resolves with the loaded configuration as a {@link JsonObject}
     */
    private Maybe<JsonObject> initConfigRetriever() {
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

        return ConfigRetriever
                .create(vertx, retrieverOptions).rxGetConfig().toMaybe();
    }

    /**
     * Begin the creation of the {@link OpenAPI3RouterFactory}
     * @param config The config loaded via the {@link ConfigRetriever}
     * @return An {@link OpenAPI3RouterFactory} {@link Maybe} to be used to complete the next Async step
     */
    private Maybe<OpenAPI3RouterFactory> provisionRouter(JsonObject config) {
        vertx.getOrCreateContext().config().mergeIn(config);
        LOG.info(vertx.getOrCreateContext().config().encodePrettily());
        service = new InsultServiceImpl(vertx.getDelegate());
        new ServiceBinder(vertx.getDelegate()).setAddress(INSULT_SERVICE).register(InsultService.class, service);
        return OpenAPI3RouterFactory.rxCreate(vertx, "/insult.yaml").toMaybe();
    }

    /**
     * Create an {@link HttpServer} and use the {@link OpenAPI3RouterFactory}
     * to handle HTTP requests
     * @param factory A {@link OpenAPI3RouterFactory} instance which is used to create a {@link Router}
     * @return The {@link HttpServer} instance created
     */
    private Maybe<HttpServer> createHttpServer(OpenAPI3RouterFactory factory) {
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
        return vertx.createHttpServer(httpConfig)
                .requestHandler(baseRouter::accept).rxListen().toMaybe();
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
