package com.hedera.hashgraph.poa;

import com.google.common.flogger.FluentLogger;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.HederaPreCheckStatusException;
import com.hedera.hashgraph.sdk.HederaReceiptStatusException;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.TopicCreateTransaction;
import com.hedera.hashgraph.sdk.TopicId;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvBuilder;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;
import org.flywaydb.core.Flyway;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static io.vertx.core.Vertx.vertx;
import static io.vertx.ext.web.Router.router;

public class App extends AbstractVerticle {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private static final Dotenv env = new DotenvBuilder().ignoreIfMissing().load();

    private static SecretKey secretKey;

    private static final AccountId hederaOperatorId = AccountId.fromString(
        Objects.requireNonNull(env.get("HEDERA_OPERATOR_ID")));

    private static final PrivateKey hederaOperatorKey = PrivateKey.fromString(
        Objects.requireNonNull(env.get("HEDERA_OPERATOR_KEY")));

    private static TopicId hederaTopicId;

    // note: this does not connect until first use
    private static final Client hederaClient = Client.forTestnet()
        .setOperator(hederaOperatorId, hederaOperatorKey);

    private HttpServer httpServer;

    public static void main(String[] args) throws TimeoutException, HederaPreCheckStatusException, HederaReceiptStatusException {
        Security.addProvider(new BouncyCastleProvider());

        // generate the secret key and announce if we needed to

        var secretKeyText = env.get("SECRET_KEY");
        if (secretKeyText == null || secretKeyText.isBlank()) {
            var random = new SecureRandom();
            var keyBytes = new byte[32];
            random.nextBytes(keyBytes);

            logger.atWarning().log("generated new encryption key: %s", new String(Hex.encode(keyBytes)));
            logger.atInfo().log("you must save and re-use this encryption key to decrypt any data transferred to Hedera");

            secretKey = new SecretKeySpec(keyBytes, "AES");
        } else {
            secretKey = new SecretKeySpec(
                Hex.decode(secretKeyText), "AES"
            );
        }

        // load and run database migrations

        Flyway.configure()
            .dataSource("jdbc:" + Objects.requireNonNull(env.get("DATABASE_URL")), env.get("DATABASE_USER"), env.get("DATABASE_PASSWORD"))
            .load()
            .migrate();

        // announce the topic ID we'll be using and/or create the topic ID if one was not provided

        var hederaTopicIdText = env.get("HEDERA_TOPIC_ID");
        if ((hederaTopicIdText == null) || (hederaTopicIdText.isEmpty())) {
            // make a new topic ID
            var transactionId = new TopicCreateTransaction()
                .setSubmitKey(hederaOperatorKey)
                .execute(hederaClient)
                    .transactionId;

            hederaTopicId = transactionId
                .getReceipt(hederaClient)
                .topicId;

            logger.atWarning().log("created new topic ID %s", hederaTopicId);
        } else {
            hederaTopicId = TopicId.fromString(hederaTopicIdText);
        }

        vertx().deployVerticle(new App());
    }

    @Override
    public void start(Promise<Void> startPromise) {
        // create the database pool
        var options = PgConnectOptions.fromUri(env.get("DATABASE_URL"))
            .setUser(env.get("DATABASE_USER"))
            .setPassword(env.get("DATABASE_PASSWORD"));

        var db = PgPool.pool(vertx, options, new PoolOptions());

        // saves the server so we can cleanly exit later
        httpServer = vertx.createHttpServer();

        var router = router(vertx);
        router.errorHandler(500, this::handleError);

        router.route()
            // handle accepting request bodies but cap it at 4 Ki
            .handler(BodyHandler.create().setBodyLimit(4096))
            // let anyone in, it's a free world
            .handler(CorsHandler.create("*"));

        router.post("/v1/action").handler(new CreateActionHandler(
            db,
            hederaClient,
            hederaOperatorId,
            hederaTopicId,
            secretKey
        ));

        var searchHandler = new SearchActionHandler(db);

        router.get("/v1/action").handler(searchHandler);
        router.post("/v1/action/search").handler(searchHandler);

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
        hederaClient.close();
        httpServer.close(stopPromise);
    }

    private void handleError(RoutingContext rx) {
        logger.atSevere().withCause(rx.failure()).log();
        rx.response().setStatusCode(500).end();
    }
}
