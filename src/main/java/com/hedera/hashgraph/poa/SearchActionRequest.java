package com.hedera.hashgraph.poa;

public class SearchActionRequest {
    public String payload;
    public String transactionId;
    public String clientId;

    SearchActionRequest() {
    }

    SearchActionRequest(String payload, String transactionId, String clientId) {
        this.payload = payload;
        this.clientId = clientId;
        this.transactionId = transactionId;
    }
}
