package org.mifos.connector.channel;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mifos.connector.channel.camel.routes.TokenWithExpiry;
import org.mifos.connector.channel.operations.TokenCache;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TokenCacheTest {

    private TokenCache tokenCache;

    @BeforeEach
    void setUp() {
        tokenCache = new TokenCache();
    }

    @Test
    void testUpdateAndGetTenantCache() {
        TokenWithExpiry token = new TokenWithExpiry("token123", Instant.now().plusSeconds(60));
        tokenCache.updateTenantCache("tenantA", token);
        TokenWithExpiry cached = tokenCache.getTenantCache("tenantA");
        assertNotNull(cached);
        assertEquals("token123", cached.token);
    }

    @Test
    void testIsExpired_TrueWhenExpired() {
        TokenWithExpiry expiredToken = new TokenWithExpiry("expired", Instant.now().minusSeconds(10));
        tokenCache.updateTenantCache("tenantB", expiredToken);
        assertTrue(tokenCache.isExpired("tenantB", 0));
    }

    @Test
    void testIsExpired_FalseWhenNotExpired() {
        TokenWithExpiry validToken = new TokenWithExpiry("valid", Instant.now().plusSeconds(100));
        tokenCache.updateTenantCache("tenantC", validToken);
        assertFalse(tokenCache.isExpired("tenantC", 0));
    }

    @Test
    void testIsExpired_TrueWhenNoToken() {
        assertTrue(tokenCache.isExpired("unknownTenant", 0));
    }

    @Test
    void testClear() {
        tokenCache.updateTenantCache("tenantD", new TokenWithExpiry("token", Instant.now().plusSeconds(60)));
        tokenCache.clear();
        assertNull(tokenCache.getTenantCache("tenantD"));
    }
}
