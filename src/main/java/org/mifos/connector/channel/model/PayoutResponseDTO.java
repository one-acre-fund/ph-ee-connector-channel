package org.mifos.connector.channel.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;
import org.mifos.connector.channel.payout.PayoutStatus;

/**
 * Answer of {@code POST /channel/payout}: what happened to the request, the transaction id whenever a payout exists or
 * may exist, and why a request did not start one.
 */
@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayoutResponseDTO {

    String transactionId;
    PayoutStatus status;
    String error;
    String details;

    public static PayoutResponseDTO started(String transactionId) {
        return new PayoutResponseDTO(transactionId, PayoutStatus.STARTED, null, null);
    }

    public static PayoutResponseDTO notStarted(String transactionId, PayoutStatus status, String error,
                                               String details) {
        return new PayoutResponseDTO(transactionId, status, error, details);
    }
}
