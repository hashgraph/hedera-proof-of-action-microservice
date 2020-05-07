package com.hedera.hashgraph.poa;

import com.hedera.hashgraph.sdk.TransactionId;

public class CreateActionResponse {
    public final String transactionId;

    CreateActionResponse(TransactionId transactionId) {
        this.transactionId = transactionId.toString();
    }
}
