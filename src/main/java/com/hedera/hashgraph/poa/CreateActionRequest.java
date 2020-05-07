package com.hedera.hashgraph.poa;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateActionRequest {
    public String payload;

    // optional, non-unique, client-generated ID
    // this can be used to filter the actions by at a later time
    public String clientId;

    public CreateActionRequestSubmit submit = CreateActionRequestSubmit.DIRECT;

    public enum CreateActionRequestSubmit {
        @JsonProperty("direct") DIRECT,
        @JsonProperty("hash") HASH,
        @JsonProperty("encrypted") ENCRYPTED
    }
}
