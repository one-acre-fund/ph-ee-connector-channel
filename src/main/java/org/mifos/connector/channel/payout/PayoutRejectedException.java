package org.mifos.connector.channel.payout;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * A payout request that was not started. Nothing was sent to a provider.
 */
@Getter
public class PayoutRejectedException extends RuntimeException {

    private final HttpStatus status;
    private final String details;

    public PayoutRejectedException(HttpStatus status, String message, String details) {
        super(message);
        this.status = status;
        this.details = details;
    }

    static PayoutRejectedException invalid(String details) {
        return new PayoutRejectedException(HttpStatus.BAD_REQUEST, "Invalid payout request", details);
    }
}
