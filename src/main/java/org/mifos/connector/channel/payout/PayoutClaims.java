package org.mifos.connector.channel.payout;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * One claim per payout transaction id, held in Redis, so the same payout is started at most once across all channel
 * pods: {@link #claim} is an atomic SET NX. A claim lives for {@code payout.claim-ttl} (30 days by default).
 */
@Component
@RequiredArgsConstructor
public class PayoutClaims {

    static final String KEY_PREFIX = "payout:";

    /**
     * State of a claimed payout.
     */
    public enum State {
        // Claimed; the workflow start is in progress
        STARTING,
        // Zeebe confirmed the workflow start
        STARTED,
        // The start was sent but not confirmed
        UNCONFIRMED
    }

    private final RedisTemplate<String, String> redisTemplate;
    private final PayoutProperties properties;

    /**
     * @return true when this call claimed the transaction id, false when it was claimed before
     * @throws org.springframework.dao.DataAccessException when Redis cannot be reached
     */
    public boolean claim(String transactionId) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(key(transactionId), State.STARTING.name(), properties.getClaimTtl()));
    }

    /**
     * @return the state of an existing claim, empty when there is none or it cannot be read
     */
    public Optional<State> state(String transactionId) {
        var value = redisTemplate.opsForValue().get(key(transactionId));
        try {
            return Optional.ofNullable(value).map(State::valueOf);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public void update(String transactionId, State state) {
        redisTemplate.opsForValue().set(key(transactionId), state.name(), properties.getClaimTtl());
    }

    /**
     * Removes the claim of a payout that certainly was not started, so it can be submitted again.
     */
    public void release(String transactionId) {
        redisTemplate.delete(key(transactionId));
    }

    private static String key(String transactionId) {
        return KEY_PREFIX + transactionId;
    }
}
