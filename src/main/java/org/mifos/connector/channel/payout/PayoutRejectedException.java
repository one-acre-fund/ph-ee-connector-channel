package org.mifos.connector.channel.payout;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * A payout request that did not start a new payout. {@link #getOutcome()} says whether one may be running: only
 * {@link PayoutStatus#REJECTED} guarantees that nothing was started; {@link PayoutStatus#DUPLICATE} and
 * {@link PayoutStatus#UNCONFIRMED} carry the transaction id to check.
 */
@Getter
public class PayoutRejectedException extends RuntimeException {

    private final HttpStatus status;
    private final PayoutStatus outcome;
    private final String transactionId;
    private final String details;

    public PayoutRejectedException(HttpStatus status, String message, String details) {
        this(status, PayoutStatus.REJECTED, null, message, details);
    }

    public PayoutRejectedException(HttpStatus status, PayoutStatus outcome, String transactionId, String message,
                                   String details) {
        super(message);
        this.status = status;
        this.outcome = outcome;
        this.transactionId = transactionId;
        this.details = details;
    }

    static PayoutRejectedException invalid(String details) {
        return new PayoutRejectedException(HttpStatus.BAD_REQUEST, "Invalid payout request", details);
    }
}
