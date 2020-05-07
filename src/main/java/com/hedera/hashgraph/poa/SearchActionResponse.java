package com.hedera.hashgraph.poa;

import com.google.common.hash.HashCode;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.TransactionId;
import io.vertx.sqlclient.Row;
import org.threeten.bp.format.DateTimeFormatter;

public class SearchActionResponse {
    public final String transactionId;

    public final String consensusTimestamp;

    public final long sequenceNumber;

    public final String runningHash;

    SearchActionResponse(Row row) {
        var transactionAccountId = row.getLong("transaction_id_num");
        var validStart = row.getLong("transaction_id_valid_start");
        var seqNum = row.getLong("sequence_number");
        var runningHash = row.getBuffer("running_hash");
        var consensusTimestamp = row.getLong("consensus_timestamp");

        this.transactionId = new TransactionId(
            new AccountId(transactionAccountId),
            InstantConverter.fromNanos(validStart)
        ).toString();

        this.sequenceNumber = seqNum;

        // noinspection UnstableApiUsage
        this.runningHash = HashCode.fromBytes(runningHash.getBytes()).toString();

        this.consensusTimestamp = DateTimeFormatter.ISO_INSTANT.format(InstantConverter.fromNanos(consensusTimestamp));
    }
}
