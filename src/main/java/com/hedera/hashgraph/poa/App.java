package com.hedera.hashgraph.poa;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.MessageSubmitTransaction;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TransactionId;
import io.github.cdimascio.dotenv.Dotenv;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java8.util.concurrent.CompletableFuture;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;
import org.threeten.bp.Instant;
import org.threeten.bp.format.DateTimeFormatter;

import javax.annotation.Nullable;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static io.vertx.core.Vertx.vertx;
import static io.vertx.ext.web.Router.router;
import static java8.util.concurrent.CompletableFuture.delayedExecutor;

public class App extends AbstractVerticle {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private static final Dotenv env = Dotenv.load();

    private final SecureRandom secureRandom = new SecureRandom();

    private final SecretKey secretKey = new SecretKeySpec(
        Hex.decode(env.get("SECRET_KEY")), "AES"
    );

    private final AccountId hederaOperatorId = AccountId.fromString(
        Objects.requireNonNull(env.get("HEDERA_OPERATOR_ID")));

    private final PrivateKey hederaOperatorKey = PrivateKey.fromString(
        Objects.requireNonNull(env.get("HEDERA_OPERATOR_KEY")));

    private final TopicId hederaTopicId = TopicId.fromString(
        Objects.requireNonNull(env.get("HEDERA_TOPIC_ID")));

    // note: this does not connect until first use
    private final Client hederaClient = Client.forTestnet()
        .setOperator(hederaOperatorId, hederaOperatorKey);

    private PgPool db;

    private HttpServer httpServer;

    public static void main(String[] args) {
        Security.addProvider(new BouncyCastleProvider());
        vertx().deployVerticle(new App());
    }

    @Override
    public void start(Promise<Void> startPromise) {
        // create the database pool
        db = PgPool.pool(vertx, env.get("DATABASE_URL"));

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
        hederaClient.close();
        httpServer.close(stopPromise);
    }

    private void handleError(RoutingContext rx) {
        logger.atSevere().withCause(rx.failure()).log();
        rx.response().setStatusCode(500).end();
    }

    private void handleSubmitAction(RoutingContext rx) {
        var req = Json.decodeValue(rx.getBody(), ActionRequest.class);
        var res = rx.response();

        // pre-generate the transaction ID so we can save the record of the action
        var transactionId = TransactionId.generate(hederaOperatorId);

        // create a future to receive the action ID
        var actionIdFut = new CompletableFuture<Long>();

        // prepare the message to submit to hedera
        byte[] messageToSubmit = req.payload.getBytes();

        switch (req.submit) {
            case DIRECT:
                break;

            case HASH:
                // noinspection UnstableApiUsage
                messageToSubmit = Hashing.sha384().hashBytes(messageToSubmit).toString().getBytes();
                break;

            case ENCRYPTED:
                Cipher cipher = null;

                try {
                    cipher = Cipher.getInstance("AES/ECB/PKCS7Padding", "BC");
                } catch (NoSuchAlgorithmException | NoSuchProviderException | NoSuchPaddingException e) {
                    throw new RuntimeException(e);
                }

                try {
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey);
                } catch (InvalidKeyException e) {
                    throw new RuntimeException(e);
                }

                try {
                    messageToSubmit = cipher.doFinal(messageToSubmit);
                } catch (IllegalBlockSizeException | BadPaddingException e) {
                    throw new RuntimeException(e);
                }

                break;
        }

        // submit to HCS
        // note: this intentionally does not block the HTTP request from progressing, we want to immediately return
        //       to the client here
        new MessageSubmitTransaction()
            .setTransactionId(transactionId)
            .setMessage(messageToSubmit)
            .setTopicId(hederaTopicId)
            .executeAsync(hederaClient)
            // note: futures flow so nicely, this is almost sync. level clarity and its async execution here
            .thenComposeAsync(id -> id.getRecordAsync(hederaClient), delayedExecutor(5, TimeUnit.SECONDS))
            .thenCombineAsync(actionIdFut, (record, actionId) -> {
                var fut = new CompletableFuture<Void>();

                var params = Tuple.of(
                    actionId,
                    instantToNanos(record.consensusTimestamp),
                    record.receipt.topicSequenceNumber,
                    Buffer.buffer(Objects.requireNonNull(record.receipt.topicRunningHash).toByteArray())
                );

                db.preparedQuery(
                    "INSERT INTO proofs ( action_id, consensus_timestamp, sequence_number, running_hash ) " +
                    "VALUES ( $1, $2, $3, $4 )"
                ).execute(params, v -> {
                    if (v.failed()) {
                        fut.completeExceptionally(v.cause());
                        return;
                    }

                    // language=text
                    fut.complete(null);
                });

                return fut;
            })
            .whenComplete((v, error) -> {
                if (error != null) {
                    // failed to submit the message
                    logger.atSevere().withCause(error).log();

                    // FIXME: as this is currently in a proof-of-concept stage, we're ignoring the potential failure
                    //        in a real application, we would want to retry or something
                }
            });

        db.preparedQuery(
            "INSERT INTO actions ( payload, transaction_id_num, transaction_id_valid_start ) " +
            "VALUES ( $1, $2, $3 ) " +
            "RETURNING id"
        ).execute(Tuple.of(req.payload, transactionId.accountId.num, instantToNanos(transactionId.validStart)), v -> {
            if (v.failed()) {
                rx.fail(v.cause());
                return;
            }

            // pull out the generated action ID
            var row = v.result().iterator().next();
            var actionId = row.getLong(0);

            // and pass the action ID to our other future
            actionIdFut.complete(actionId);

            try {
                // 202 -> ACCEPTED
                // the idea is we record the action but we are pending on the proof
                res.setStatusCode(202)
                    .putHeader("content-type", "application/json")
                    .end(Json.encode(new PendingActionResponse(transactionId.toString())));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void handleFindAction(RoutingContext rx) {
        var res = rx.response();
        try {
            var handler = Promise.<RowSet<ActionResponse>>promise();

            var queryPayload = rx.request().getParam("payload");
            if (queryPayload != null) {
                db.preparedQuery(
                    "SELECT a.transaction_id_num, a.transaction_id_valid_start, p.sequence_number, p.running_hash, p.consensus_timestamp " +
                    "FROM proofs p " +
                    "INNER JOIN actions a ON a.id = p.action_id " +
                    "WHERE a.payload = $1"
                ).mapping(ActionResponse::new).execute(Tuple.of(queryPayload), handler);
            }

            var queryTransactionId = rx.request().getParam("transactionId");
            if (queryTransactionId != null) {
                var transactionId = transactionIdFromString(queryTransactionId);

                db.preparedQuery(
                    "SELECT a.transaction_id_num, a.transaction_id_valid_start, p.sequence_number, p.running_hash, p.consensus_timestamp " +
                    "FROM proofs p " +
                    "INNER JOIN actions a ON a.id = p.action_id " +
                    "WHERE a.transaction_id_num = $1 " +
                    "AND a.transaction_id_valid_start = $2"
                ).mapping(ActionResponse::new).execute(Tuple.of(transactionId.accountId.num, instantToNanos(transactionId.validStart)), handler);
            }

            if (queryTransactionId == null && queryPayload == null) {
                // need at least one query parameter
                res.setStatusCode(400).end();
                return;
            }

            handler.future().onComplete(v -> {
                if (v.failed()) {
                    rx.fail(v.cause());
                    return;
                }

                var rows = v.result();
                var results = StreamSupport.stream(rows.spliterator(), false).collect(Collectors.toUnmodifiableList());

                if (results.size() == 0) {
                    // no dice
                    res.setStatusCode(404).end();
                    return;
                }

                res.setStatusCode(200)
                    .putHeader("content-type", "application/json")
                    .end(Json.encode(results));
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // converts a Java Instant to nanoseconds for storage
    private long instantToNanos(Instant instant) {
        return (instant.getEpochSecond() * 1000000000) + instant.getNano();
    }

    private static Instant nanosToInstant(long nanos) {
        var seconds = nanos / 1000000000;
        var fracNanos = nanos % 1000000000;

        return Instant.ofEpochSecond(seconds, fracNanos);
    }

    // convert a String to a Hedera Transaction ID
    // FIXME: put back to the SDK
    private TransactionId transactionIdFromString(String s) {
        // FIXME: error handling here
        var parts = s.split("@");
        var accountIdS = parts[0];
        var timestampParts = parts[1].split("\\.");
        var secondsS = timestampParts[0];
        var nanosS = timestampParts[1];

        var timestamp = Instant.ofEpochSecond(Long.parseLong(secondsS), Long.parseLong(nanosS));
        var accountId = AccountId.fromString(accountIdS);

        return new TransactionId(accountId, timestamp);
    }
//
//    // encrypt with AES
//    private byte[] encryptAes(byte[] message) {
////        byte[] iv = new byte[128/8];
////        secureRandom.nextBytes(iv);
////        IvParameterSpec ivspec = new IvParameterSpec(iv);
//
//    }

    private enum ActionRequestSubmit {
        @JsonProperty("direct") DIRECT,
        @JsonProperty("hash") HASH,
        @JsonProperty("encrypted") ENCRYPTED
    }

    private static class ActionRequest {
        public String payload;
        public ActionRequestSubmit submit = ActionRequestSubmit.DIRECT;
    }

    private static class PendingActionResponse {
        public final String transactionId;

        PendingActionResponse(String transactionId) {
            this.transactionId = transactionId;
        }
    }

    private static class ActionResponse {
        public final String transactionId;

        public final String consensusTimestamp;

        public final long sequenceNumber;

        public final String runningHash;

        ActionResponse(Row row) {
            var transactionAccountId = row.getLong("transaction_id_num");
            var validStart = row.getLong("transaction_id_valid_start");
            var seqNum = row.getLong("sequence_number");
            var runningHash = row.getBuffer("running_hash");
            var consensusTimestamp = row.getLong("consensus_timestamp");

            this.transactionId = new TransactionId(
                new AccountId(transactionAccountId),
                nanosToInstant(validStart)
            ).toString();

            this.sequenceNumber = seqNum;

            // noinspection UnstableApiUsage
            this.runningHash = HashCode.fromBytes(runningHash.getBytes()).toString();

            this.consensusTimestamp = DateTimeFormatter.ISO_INSTANT.format(nanosToInstant(consensusTimestamp));
        }
    }
}
