package com.redhat.labs.adjective;

import com.redhat.labs.adjective.services.AdjectiveService;
import com.redhat.labs.adjective.services.AdjectiveServiceImpl;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.Maybe;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AsyncResult;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.api.RequestParameter;
import io.vertx.ext.web.api.RequestParameters;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Future;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;
import io.vertx.serviceproxy.ServiceBinder;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import java.sql.Connection;
import java.sql.DriverManager;

import static io.netty.handler.codec.http.HttpResponseStatus.*;

public class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    AdjectiveService service;

    /**
     * Initialize and start the {@link MainVerticle}
     * @param startFuture An instance of {@link Future} which allows us to report on the startup status.
     */
    @Override
    public void start(io.vertx.core.Future<Void> startFuture) {
        this.initConfigRetriever()                      // Attempt to retrieve the application's configuration
            .flatMap(this::asyncLoadDbSchema)           // Use the config to load the DB schema
            .flatMap(this::provisionRouter)             // Provision the OpenAPI3RouterFactory
            .flatMap(this::createHttpServer)            // Mount the OpenAPI3Router and create the HTTP server
            .doOnError(startFuture::fail)               // If ANY step fails, fail the Verticle deployment
            .subscribe(m -> startFuture.complete());    // If ALL steps succeed, complete the Verticle deployment
    }

    /**
     * Initialize the {@link ConfigRetriever} and return a {@link Future}
     * @return A {@link Future} which resolves with the loaded configuration as a {@link JsonObject}
     */
    Maybe<JsonObject> initConfigRetriever() {
        ConfigStoreOptions defaultOpts = new ConfigStoreOptions()
                .setType("file")
                .setFormat("json")
                .setConfig(new JsonObject().put("path", "adj_default_config.json"));

        ConfigRetrieverOptions retrieverOptions = new ConfigRetrieverOptions()
                                            .addStore(defaultOpts);

        // Check to see if we are running on Kubernetes/OCP
        if (System.getenv().containsKey("KUBERNETES_NAMESPACE")) {

            ConfigStoreOptions confOpts = new ConfigStoreOptions()
                    .setType("configmap")
                    .setConfig(new JsonObject()
                            .put("name", "adjective-config")
                            .put("optional", true)
                    );
            retrieverOptions.addStore(confOpts);
        }

        return ConfigRetriever.create(vertx, retrieverOptions).rxGetConfig().toMaybe();
    }

    /**
     * Load the database schema from the config via Liquibase
     * @param config A {@link JsonObject} containing the configuration retrieved in the previous step.
     * @return A {@link Void} {@link Future} to be used to complete the next Async step
     */
    Maybe<Boolean> asyncLoadDbSchema(JsonObject config) {
        vertx.getOrCreateContext().config().mergeIn(config);
        LOG.info(vertx.getOrCreateContext().config().encodePrettily());
        return vertx.rxExecuteBlocking(this::loadDbSchema).toMaybe();
    }

    /**
     * Synchronous method to use Liquibase to load the database schema
     * @param f A {@link Future} to be completed when operation is done
     */
    void loadDbSchema(io.vertx.reactivex.core.Future<Boolean> f) {
        try {
            JsonObject dbCfg = vertx.getOrCreateContext().config().getJsonObject("db");
            Class.forName(dbCfg.getString("driver_class"));
            try (Connection conn = DriverManager.getConnection(
                    dbCfg.getString("url"),
                    dbCfg.getString("user"),
                    dbCfg.getString("password"))) {
                Database database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(conn));
                Liquibase liquibase = new Liquibase("adjective_schema.xml", new ClassLoaderResourceAccessor(), database);
                liquibase.update(new Contexts(), new LabelExpression());
                f.complete(Boolean.TRUE);
            }
        } catch (Exception e) {
            if (e.getCause().getLocalizedMessage().contains("already exists"))
                if (e.getCause() != null) {
                    f.complete(Boolean.TRUE);
                } else {
                    f.fail(e);
                }
            else {
                f.fail(e);
            }
        }
    }

    /**
     * Begin the creation of the {@link OpenAPI3RouterFactory}
     * @return An {@link OpenAPI3RouterFactory} {@link Future} to be used to complete the next Async step
     */
    Maybe<OpenAPI3RouterFactory> provisionRouter(Boolean b) {
        service = new AdjectiveServiceImpl(vertx.getDelegate());
        new ServiceBinder(vertx.getDelegate()).setAddress("adjective.service").register(AdjectiveService.class, service);
        return OpenAPI3RouterFactory.rxCreate(vertx, "/adjective.yaml").toMaybe();
    }

    /**
     * Create an {@link HttpServer} and use the {@link OpenAPI3RouterFactory}
     * to handle HTTP requests
     * @param factory A {@link OpenAPI3RouterFactory} instance which is used to create a {@link Router}
     * @return The {@link HttpServer} instance created
     */
    Maybe<HttpServer> createHttpServer(OpenAPI3RouterFactory factory) {
        Router baseRouter = Router.router(vertx);
        baseRouter.route().handler(this::logRequestPath);

        factory.addHandlerByOperationId("getAdjective", ctx -> service.get(res -> this.handleResult(ctx, OK, res)));
        factory.addHandlerByOperationId("addAdjective", this::handleAdjPost);
        factory.addHandlerByOperationId("health", ctx -> service.check(res -> this.handleResult(ctx, OK, res)));

        JsonObject httpJsonCfg = vertx.getOrCreateContext().config().getJsonObject("http");

        baseRouter.mountSubRouter("/api/v1", factory.getRouter());

        HttpServerOptions httpConfig = new HttpServerOptions(httpJsonCfg);
        return vertx.createHttpServer(httpConfig)
                .requestHandler(baseRouter::accept)
                .rxListen().toMaybe();
    }

    /**
     * Log the request path given in the {@link RoutingContext}
     * @param ctx The {@link RoutingContext} of a request
     */
    void logRequestPath(RoutingContext ctx) {
        LOG.info(ctx.request().path());
        ctx.next();
    }

    /**
     * Extract POST parameters from the {@link RoutingContext} and make the call to the Service Proxy method
     * @param ctx The {@link RoutingContext} of the request being handled
     */
    void handleAdjPost(RoutingContext ctx) {
        RequestParameters params = ctx.get("parsedParameters");
        RequestParameter bodyParam = params.body();
        JsonObject data = bodyParam.getJsonObject();
        service.save(data.getString("adjective"), res -> this.handleResult(ctx, OK, res));
    }

    /**
     * Handles a Service Proxy response and uses the {@link RoutingContext} to send the response
     * @param ctx The {@link RoutingContext} of the request we are responding to
     * @param status The {@link HttpResponseStatus} to be used for the request's successful response
     * @param res The {@link JsonObject} which contains a response
     */
    void handleResult(RoutingContext ctx, HttpResponseStatus status, AsyncResult<String> res) {
        if (res.succeeded()) {
            ctx.response()
                .setStatusCode(status.code())
                .setStatusMessage(status.reasonPhrase())
                .putHeader("Content-Type", "application/json")
                .end(res.result());
        } else {
            this.handleFailure(ctx);
        }
    }

    /**
     * If the result of the Service Proxy call was a failed future, then send the appropriate response to the HTTP
     * client.
     * @param ctx The {@link RoutingContext} of the request being handled
     */
    void handleFailure(RoutingContext ctx) {
        ctx.response()
            .setStatusCode(INTERNAL_SERVER_ERROR.code())
            .setStatusMessage(INTERNAL_SERVER_ERROR.reasonPhrase())
            .end();
    }
}