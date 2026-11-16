package org.mifos.connector.channel.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;

/**
 * Answer of {@code POST /channel/payout}: the transaction id when started, or why it was not.
 */
@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayoutResponseDTO {

    String transactionId;
    String error;
    String details;

    public static PayoutResponseDTO started(String transactionId) {
        return new PayoutResponseDTO(transactionId, null, null);
    }

    public static PayoutResponseDTO rejected(String error, String details) {
        return new PayoutResponseDTO(null, error, details);
    }
}
