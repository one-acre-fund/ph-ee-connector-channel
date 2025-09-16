package org.mifos.connector.channel.camel.routes;

import java.time.Instant;

public class TokenWithExpiry {
    final String token;
    final java.time.Instant expiresAt;

    TokenWithExpiry(String token, java.time.Instant expiresAt) {
        this.token = token;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(long bufferSeconds) {
        return Instant.now().isAfter(expiresAt.minusSeconds(bufferSeconds));
    }
}
