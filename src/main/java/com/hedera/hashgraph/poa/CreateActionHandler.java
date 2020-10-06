package com.hedera.hashgraph.poa;

import com.google.common.flogger.FluentLogger;
import com.google.common.hash.Hashing;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.TopicMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TransactionId;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import java.util.concurrent.CompletableFuture;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.CompletableFuture.delayedExecutor;

public class CreateActionHandler implements Handler<RoutingContext> {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private final PgPool db;

    private final Client hedera;

    private final AccountId hederaOperatorId;

    private final TopicId hederaTopicId;

    private final SecretKey encryptionSecretKey;

    public CreateActionHandler(PgPool db, Client hedera, AccountId hederaOperatorId, TopicId hederaTopicId, SecretKey encryptionSecretKey) {
        this.db = db;
        this.hedera = hedera;
        this.hederaTopicId = hederaTopicId;
        this.hederaOperatorId = hederaOperatorId;
        this.encryptionSecretKey = encryptionSecretKey;
    }

    @Override
    public void handle(RoutingContext rx) {
        var res = rx.response();

        var reqBody = rx.getBody();
        if (reqBody.length() == 0) {
            // no content, instant failure
            res.setStatusCode(400);
            res.end();
            return;
        }

        var req = Json.decodeValue(rx.getBody(), CreateActionRequest.class);

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
                    cipher.init(Cipher.ENCRYPT_MODE, encryptionSecretKey);
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
        new TopicMessageSubmitTransaction()
            .setTransactionId(transactionId)
            .setMessage(messageToSubmit)
            .setTopicId(hederaTopicId)
            .executeAsync(hedera)
            // note: futures flow so nicely, this is almost sync. level clarity and its async execution here
            .thenComposeAsync(id -> id.transactionId.getRecordAsync(hedera), delayedExecutor(5, TimeUnit.SECONDS))
            .thenCombineAsync(actionIdFut, (record, actionId) -> {
                var fut = new CompletableFuture<Void>();

                var params = Tuple.of(
                    actionId,
                    InstantConverter.toNanos(record.consensusTimestamp),
                    record.receipt.topicSequenceNumber,
                    Buffer.buffer(Objects.requireNonNull(record.receipt.topicRunningHash).toByteArray()),
                    Buffer.buffer(record.transactionHash.toByteArray())
                );

                db.preparedQuery(
                    "INSERT INTO proofs ( action_id, consensus_timestamp, sequence_number, running_hash, transaction_hash ) " +
                    "VALUES ( $1, $2, $3, $4, $5 )"
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
            "INSERT INTO actions ( payload, transaction_id_num, transaction_id_valid_start, client_id ) " +
            "VALUES ( $1, $2, $3, $4 ) " +
            "RETURNING id"
        ).execute(Tuple.of(req.payload, transactionId.accountId.num, InstantConverter.toNanos(transactionId.validStart), req.clientId), v -> {
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
                    .end(Json.encode(new CreateActionResponse(transactionId)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
