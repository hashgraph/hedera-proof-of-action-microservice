package com.hedera.hashgraph.poa;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateActionRequest {
    public String payload;
    public CreateActionRequestSubmit submit = CreateActionRequestSubmit.DIRECT;

    public enum CreateActionRequestSubmit {
        @JsonProperty("direct") DIRECT,
        @JsonProperty("hash") HASH,
        @JsonProperty("encrypted") ENCRYPTED
    }
}
