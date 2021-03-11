package com.hedera.hashgraph.poa;

import java.time.Instant;

public class InstantConverter {
    private InstantConverter() {}

    // converts a Java Instant to nanoseconds for storage
    public static long toNanos(Instant instant) {
        return (instant.getEpochSecond() * 1000000000) + instant.getNano();
    }

    public static Instant fromNanos(long nanos) {
        var seconds = nanos / 1000000000;
        var fracNanos = nanos % 1000000000;

        return Instant.ofEpochSecond(seconds, fracNanos);
    }
}
