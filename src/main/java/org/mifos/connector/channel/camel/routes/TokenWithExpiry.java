package org.mifos.connector.channel.camel.routes;

import java.time.Instant;

public class TokenWithExpiry {
    public final String token;
    final java.time.Instant expiresAt;

    public TokenWithExpiry(String token, java.time.Instant expiresAt) {
        this.token = token;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(long bufferSeconds) {
        return Instant.now().isAfter(expiresAt.minusSeconds(bufferSeconds));
    }
}
