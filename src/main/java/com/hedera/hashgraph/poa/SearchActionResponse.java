package com.hedera.hashgraph.poa;

import com.google.common.hash.HashCode;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.TransactionId;
import io.vertx.sqlclient.Row;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class SearchActionResponse {
    public final String transactionId;

    public final String consensusTimestamp;

    public final long sequenceNumber;

    public final String runningHash;

    public final String clientId;

    public final String transactionHash;

    SearchActionResponse(Row row) {
        var transactionAccountId = row.getLong("transaction_id_num");
        var validStart = row.getLong("transaction_id_valid_start");
        var seqNum = row.getLong("sequence_number");
        var runningHash = row.getBuffer("running_hash");
        var transactionHash = row.getBuffer("transaction_hash");
        var consensusTimestamp = row.getLong("consensus_timestamp");

        this.transactionId = new TransactionId(
            new AccountId(transactionAccountId),
                Instant.ofEpochSecond(0, validStart)
        ).toString();

        this.sequenceNumber = seqNum;
        this.clientId = row.getString("client_id");

        // noinspection UnstableApiUsage
        this.runningHash = HashCode.fromBytes(runningHash.getBytes()).toString();

        if (transactionHash != null) {
            // noinspection UnstableApiUsage
            this.transactionHash = HashCode.fromBytes(transactionHash.getBytes()).toString();
        } else {
            this.transactionHash = null;
        }

        this.consensusTimestamp = DateTimeFormatter.ISO_INSTANT.format(InstantConverter.fromNanos(consensusTimestamp));
    }
}
