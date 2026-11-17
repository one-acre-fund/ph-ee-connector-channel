package org.mifos.connector.channel.payout;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayoutClaimsTest {

    private final RedisTemplate<String, String> redisTemplate = mockRedisTemplate();
    private final ValueOperations<String, String> values = mockValues();
    private final PayoutProperties properties = new PayoutProperties();
    private PayoutClaims claims;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        properties.setClaimTtl(Duration.ofDays(7));
        claims = new PayoutClaims(redisTemplate, properties);
    }

    @Test
    void claim_shouldSetTheKeyOnlyIfAbsentWithTheTtl() {
        when(values.setIfAbsent("payout:tx-1", "STARTING", Duration.ofDays(7))).thenReturn(true);
        when(values.setIfAbsent("payout:tx-2", "STARTING", Duration.ofDays(7))).thenReturn(false);

        assertTrue(claims.claim("tx-1"));
        assertFalse(claims.claim("tx-2"));
    }

    @Test
    void claim_withoutAnswerFromRedis_shouldNotClaim() {
        when(values.setIfAbsent("payout:tx-1", "STARTING", Duration.ofDays(7))).thenReturn(null);

        assertFalse(claims.claim("tx-1"));
    }

    @Test
    void state_shouldReadTheStoredState() {
        when(values.get("payout:tx-1")).thenReturn("UNCONFIRMED");
        when(values.get("payout:tx-2")).thenReturn(null);
        when(values.get("payout:tx-3")).thenReturn("garbage");

        assertEquals(Optional.of(PayoutClaims.State.UNCONFIRMED), claims.state("tx-1"));
        assertEquals(Optional.empty(), claims.state("tx-2"));
        assertEquals(Optional.empty(), claims.state("tx-3"));
    }

    @Test
    void updateAndRelease_shouldWriteAndDeleteTheKey() {
        claims.update("tx-1", PayoutClaims.State.STARTED);
        claims.release("tx-1");

        verify(values).set("payout:tx-1", "STARTED", Duration.ofDays(7));
        verify(redisTemplate).delete("payout:tx-1");
    }

    @SuppressWarnings("unchecked")
    private static RedisTemplate<String, String> mockRedisTemplate() {
        return mock(RedisTemplate.class);
    }

    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> mockValues() {
        return mock(ValueOperations.class);
    }
}
