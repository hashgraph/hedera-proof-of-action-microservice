package com.hedera.hashgraph.poa;

import com.google.common.flogger.FluentLogger;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.HashSet;
import java.util.Set;

import static io.vertx.core.Vertx.vertx;
import static io.vertx.ext.web.Router.router;

public class App extends AbstractVerticle {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private Set<String> actions = new HashSet<>();

    private HttpServer httpServer;

    public static void main(String[] args) {
        vertx().deployVerticle(new App());
    }

    @Override
    public void start(Promise<Void> startPromise) {
        // saves the server so we can cleanly exit later
        httpServer = vertx.createHttpServer();

        var router = router(vertx);
        router.errorHandler(500, this::handleError);

        // handle accepting request bodies but cap it at 4 Ki
        router.route().handler(BodyHandler.create().setBodyLimit(4096));

        router.post("/v1/action").handler(this::handleSubmitAction);
        router.get("/v1/action").handler(this::handleFindAction);

        httpServer.requestHandler(router);
        httpServer.listen(8080, "0.0.0.0", v -> {
            if (v.succeeded()) {
                logger.atInfo().log("listening on http://0.0.0.0:8080/");

                startPromise.complete();
            } else {
                startPromise.fail(v.cause());
            }
        });
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        httpServer.close(stopPromise);
    }

    private void handleError(RoutingContext rx) {
        logger.atSevere().withCause(rx.failure()).log();
        rx.response().setStatusCode(500).end();
    }

    private void handleSubmitAction(RoutingContext rx) {
        var payload = rx.getBodyAsString();
        var res = rx.response();

        if (payload.isEmpty()) {
            // the payload should at least be *something*
            res.setStatusCode(400).end();
            return;
        }

        // TODO: submit to HCS

        // TODO: use persistence
        // prove

        logger.atInfo().log(payload);

        actions.add(payload);

        res.setStatusCode(201).end();
    }

    private void handleFindAction(RoutingContext rx) {
        var res = rx.response();

        // TODO: use persistence

        // TODO: lookup by ID

        var queryPayload = rx.request().getParam("payload");
        if (queryPayload != null) {
            if (actions.contains(queryPayload)) {
                res.end("found");
                return;
            }
        }

        // no dice
        res.setStatusCode(404).end();
    }
}
