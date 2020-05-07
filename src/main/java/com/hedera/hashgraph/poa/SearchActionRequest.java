package com.hedera.hashgraph.poa;

public class SearchActionRequest {
    public String payload;
    public String transactionId;

    SearchActionRequest() {
    }

    SearchActionRequest(String payload, String transactionId) {
        this.payload = payload;
        this.transactionId = transactionId;
    }
}
