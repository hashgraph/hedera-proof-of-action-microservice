package com.hedera.hashgraph.poa;

import com.google.common.flogger.FluentLogger;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.TransactionId;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.threeten.bp.Instant;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class SearchActionHandler implements Handler<RoutingContext> {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private final PgPool db;

    public SearchActionHandler(PgPool db) {
        this.db = db;
    }

    @Override
    public void handle(RoutingContext rx) {
        var res = rx.response();

        SearchActionRequest req;

        if (rx.request().method() == HttpMethod.GET) {
            req = new SearchActionRequest(
                rx.request().getParam("payload"),
                rx.request().getParam("transactionId")
            );
        } else {
            var reqBody = rx.getBody();
            if (reqBody.length() == 0) {
                // no content, instant failure
                res.setStatusCode(400);
                res.end();
                return;
            }

            req = Json.decodeValue(reqBody, SearchActionRequest.class);
        }

        try {
            var handler = Promise.<RowSet<SearchActionResponse>>promise();

            if (req.payload != null) {
                db.preparedQuery(
                    "SELECT a.transaction_id_num, a.transaction_id_valid_start, p.sequence_number, p.running_hash, p.consensus_timestamp " +
                    "FROM proofs p " +
                    "INNER JOIN actions a ON a.id = p.action_id " +
                    "WHERE a.payload = $1"
                ).mapping(SearchActionResponse::new).execute(Tuple.of(req.payload), handler);
            }

            if (req.transactionId != null) {
                var transactionId = transactionIdFromString(req.transactionId);

                db.preparedQuery(
                    "SELECT a.transaction_id_num, a.transaction_id_valid_start, p.sequence_number, p.running_hash, p.consensus_timestamp " +
                    "FROM proofs p " +
                    "INNER JOIN actions a ON a.id = p.action_id " +
                    "WHERE a.transaction_id_num = $1 " +
                    "AND a.transaction_id_valid_start = $2"
                ).mapping(SearchActionResponse::new).execute(Tuple.of(transactionId.accountId.num, InstantConverter.toNanos(transactionId.validStart)), handler);
            }

            if (req.transactionId == null && req.payload == null) {
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

    // convert a String to a Hedera Transaction ID
    // FIXME: put in the SDK
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
}
