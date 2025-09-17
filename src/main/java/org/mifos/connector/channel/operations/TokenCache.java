package org.mifos.connector.channel.operations;

import org.mifos.connector.channel.camel.routes.TokenWithExpiry;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenCache {

    private final Map<String, TokenWithExpiry> cache = new ConcurrentHashMap<>();

    public void updateTenantCache(String tenantId, TokenWithExpiry token) {
        cache.put(tenantId, token);
    }

    public TokenWithExpiry getTenantCache(String tenantId) {
        return cache.get(tenantId);
    }

    public boolean isExpired(String tenantId, long bufferSeconds) {
        TokenWithExpiry token = cache.get(tenantId);
        return token == null || token.isExpired(bufferSeconds);
    }

    public void clear() {
        cache.clear();
    }
}

