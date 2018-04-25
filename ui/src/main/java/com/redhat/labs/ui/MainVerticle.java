package com.redhat.labs.ui;

import io.reactivex.Maybe;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.StaticHandler;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;

public class MainVerticle extends AbstractVerticle {

    private final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        getConfig()
                .flatMap(this::provisionRouter)
                .flatMap(this::provisionHttpServer)
                .doOnError(startFuture::fail)
                .subscribe(h -> startFuture.complete());
    }

    /**
     * Retrieve the configuration (possibly from Kubernetes ConfigMap) and return the result as a {@link Maybe}
     * @return A {@link Maybe} which may contain the configuration as a {@link JsonObject}
     */
    Maybe<JsonObject> getConfig() {
        ConfigStoreOptions localConfig = new ConfigStoreOptions()
                .setType("file")
                .setFormat("json")
                .setConfig(new JsonObject().put("path", "/opt/default_config.json"))
                .setOptional(true);

        ConfigRetrieverOptions retrieverOptions = new ConfigRetrieverOptions()
                .addStore(localConfig);
        // Check to see if we are running on Kubernetes/OCP
        if (System.getenv().containsKey("KUBERNETES_NAMESPACE")) {

            ConfigStoreOptions confOpts = new ConfigStoreOptions()
                    .setType("configmap")
                    .setConfig(new JsonObject()
                            .put("name", "ui-config")
                            .put("optional", true)
                    );
            retrieverOptions.addStore(confOpts);
        }
        return ConfigRetriever.create(vertx, retrieverOptions).rxGetConfig().toMaybe();
    }

    /**
     * Provision the router based on the configuration retrieved
     * @param cfg The {@link JsonObject} containing the configuration for the application
     * @return A {@link Maybe} which may contain the {@link Router} instance
     */
    Maybe<Router> provisionRouter(JsonObject cfg) {
        vertx.getOrCreateContext().config().mergeIn(cfg);
        Router router = Router.router(vertx);
        router.route("/api/v1/health").handler(this::healthCheck);
        if (cfg.containsKey("settings.js")) {
            router.route("/statics/js/settings.js").handler(this::getConfig);
        }
        router.route().handler(StaticHandler.create("webroot").setIndexPage("index.html"));
        return Maybe.just(router);
    }

    /**
     * Provision the {@link HttpServer} using the {@link Router} instance
     * @param router A {@link Router} instance
     * @return A {@link Maybe} which is completed if/when the {@link HttpServer} is created
     */
    Maybe<HttpServer> provisionHttpServer(Router router) {
        return vertx.createHttpServer().requestHandler(router::accept).rxListen(8080, "0.0.0.0").toMaybe();
    }

    /**
     * Handle requests for the "settings.js" file when running in Kubernetes/OpenShift
     * @param ctx An instance of {@link RoutingContext} which represents the HTTP request
     */
    void getConfig(RoutingContext ctx) {
        ctx.response().headers().add("Content-Type", "application/javascript");
        ctx.response().end(config().getString("settings.js"));
    }

    /**
     * Handle requests for the "/health" endpoint
     * @param ctx An instance of {@link RoutingContext} which represents the HTTP request
     */
    void healthCheck(RoutingContext ctx) {
        ctx.response().setStatusCode(OK.code()).setStatusMessage(OK.reasonPhrase()).end();
    }
}
