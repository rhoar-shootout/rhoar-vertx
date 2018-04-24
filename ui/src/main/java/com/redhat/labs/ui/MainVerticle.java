package com.redhat.labs.ui;

import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.StaticHandler;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;

public class MainVerticle extends AbstractVerticle {

    private final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        Router router = Router.router(vertx);
        router.route("/api/v1/health").handler(this::healthCheck);

        ConfigRetrieverOptions retrieverOptions = new ConfigRetrieverOptions();
        // Check to see if we are running on Kubernetes/OCP
        if (System.getenv().containsKey("KUBERNETES_NAMESPACE")) {
            router.route("/statics/js/settings.js").handler(this::getConfig);
            router.route().handler(StaticHandler.create("webroot").setIndexPage("index.html"));

            ConfigStoreOptions confOpts = new ConfigStoreOptions()
                    .setType("configmap")
                    .setConfig(new JsonObject()
                            .put("name", "ui-config")
                            .put("optional", true)
                    );
            retrieverOptions.addStore(confOpts);
        } else {
            router.route().handler(StaticHandler.create("webroot").setIndexPage("index.html"));
        }

        ConfigRetriever.create(vertx, retrieverOptions).rxGetConfig()
            .doOnError(startFuture::fail)
            .subscribe(cfg -> {
                vertx.getOrCreateContext().config().mergeIn(cfg);

                vertx.createHttpServer().requestHandler(router::accept).listen(8080, "0.0.0.0", httpRes -> {
                    if (httpRes.succeeded()) {
                        startFuture.complete();
                    } else {
                        startFuture.fail(httpRes.cause());
                    }
                });
            });
    }

    private void getConfig(RoutingContext ctx) {
        ctx.response().headers().add("Content-Type", "application/javascript");
        ctx.response().end(config().getString("settings.js"));
    }

    private void healthCheck(RoutingContext ctx) {
        ctx.response().setStatusCode(OK.code()).setStatusMessage(OK.reasonPhrase()).end();
    }
}
