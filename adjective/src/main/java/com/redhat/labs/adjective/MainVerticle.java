package com.redhat.labs.adjective;

import com.redhat.labs.adjective.services.AdjectiveService;
import com.redhat.labs.adjective.services.AdjectiveServiceImpl;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.RequestParameter;
import io.vertx.ext.web.api.RequestParameters;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;
import io.vertx.serviceproxy.ServiceBinder;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.resource.ClassLoaderResourceAccessor;

import java.sql.Connection;
import java.sql.DriverManager;

import static io.netty.handler.codec.http.HttpResponseStatus.*;

public class MainVerticle extends AbstractVerticle {

    private AdjectiveService service;

    /**
     * Initialize and start the {@link MainVerticle}
     * @param startFuture An instance of {@link Future} which allows us to report on the startup status.
     */
    @Override
    public void start(Future<Void> startFuture) {
        // ConfigStore from Kube/OCPs
        this.initConfigRetriever()
            .compose(this::asyncLoadDbSchema)
            .compose(v -> this.provisionRouter())
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
                .setConfig(new JsonObject().put("path", "adj_default_config.json"));

        ConfigRetrieverOptions retrieverOptions = new ConfigRetrieverOptions()
                                            .addStore(defaultOpts);

        // Check to see if we are running on Kubernetes/OCP
        if (System.getenv().containsKey("KUBERNETES_NAMESPACE")) {

            ConfigStoreOptions confOpts = new ConfigStoreOptions()
                    .setType("configmap")
                    .setConfig(new JsonObject()
                            .put("name", "adjective_config")
                            .put("optional", true)
                    );
            retrieverOptions.addStore(confOpts);
        }

        ConfigRetriever.create(vertx, retrieverOptions).getConfig(configFuture.completer());
        return configFuture;
    }

    /**
     * Load the database schema from the config via Liquibase
     * @param config A {@link JsonObject} containing the configuration retrieved in the previous step.
     * @return A {@link Void} {@link Future} to be used to complete the next Async step
     */
    private Future<Void> asyncLoadDbSchema(JsonObject config) {
        vertx.getOrCreateContext().config().mergeIn(config);
        final Future<Void> future = Future.future();
        vertx.executeBlocking(this::loadDbSchema, future.completer());
        return future;
    }

    /**
     * Synchronous method to use Liquibase to load the database schema
     * @param f A {@link Future} to be completed when operation is done
     */
    private void loadDbSchema(Future<Void> f) {
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
                f.complete();
            }
        } catch (Exception e) {
            if (e.getCause()!=null && e.getCause().getLocalizedMessage().contains("already exists")) {
                f.complete();
            } else {
                f.fail(e);
            }
        }
    }

    /**
     * Begin the creation of the {@link OpenAPI3RouterFactory}
     * @param v A Void for continuity in the async compoprovisionedsition
     * @return An {@link OpenAPI3RouterFactory} {@link Future} to be used to complete the next Async step
     */
    private Future<OpenAPI3RouterFactory> provisionRouter() {
        service = new AdjectiveServiceImpl(vertx);
        new ServiceBinder(vertx).setAddress("adjective.service").register(AdjectiveService.class, service);
        Future<OpenAPI3RouterFactory> future = Future.future();
        CircuitBreaker breaker = CircuitBreaker.create("openApi", vertx, new CircuitBreakerOptions()
                .setMaxFailures(5) // number of failure before opening the circuit
                .setTimeout(200000) // consider a failure if the operation does not succeed in time
                .setFallbackOnFailure(false) // do we call the fallback on failure
                .setResetTimeout(1000000));
        breaker.<OpenAPI3RouterFactory>execute(f ->
            OpenAPI3RouterFactory.create(
                    vertx,
                    "/adjective.yaml",
                    future.completer())
        ).setHandler(future.completer());
        return future;
    }

    /**
     * Create an {@link HttpServer} and use the {@link OpenAPI3RouterFactory}
     * to handle HTTP requests
     * @param factory A {@link OpenAPI3RouterFactory} instance which is used to create a {@link Router}
     * @return The {@link HttpServer} instance created
     */
    private Future<HttpServer> createHttpServer(OpenAPI3RouterFactory factory) {
        factory.addHandlerByOperationId("getAdjective", ctx -> service.get(res -> handleResponse(ctx, OK, res)));
        factory.addHandlerByOperationId("addAdjective", this::handleAdjPost);
        factory.addHandlerByOperationId("health", ctx -> service.check(res -> handleResponse(ctx, OK, res)));
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

    private void handleAdjPost(RoutingContext ctx) {
        RequestParameters params = ctx.get("parsedParameters");
        RequestParameter bodyParam = params.body();
        JsonObject data = bodyParam.getJsonObject();
        service.save(data.getString("adjective"),
            res -> handleResponse(ctx, CREATED, res)
        );
    }

    /**
     * Handles a Service Proxy response and uses the {@link RoutingContext} to send the response
     * @param ctx The {@link RoutingContext} of the request we are responding to
     * @param status The {@link HttpResponseStatus} to be used for the request's successful response
     * @param res The {@link AsyncResult} which contains a JSON body for the response or an exception
     */
    private void handleResponse(RoutingContext ctx, HttpResponseStatus status, AsyncResult<String> res) {
        if (res.succeeded()) {
            ctx.response()
                    .setStatusCode(status.code())
                    .setStatusMessage(status.reasonPhrase())
                    .end(res.result());
        } else {
            ctx.response()
                    .setStatusCode(INTERNAL_SERVER_ERROR.code())
                    .setStatusMessage(INTERNAL_SERVER_ERROR.reasonPhrase())
                    .end();
        }
    }
}