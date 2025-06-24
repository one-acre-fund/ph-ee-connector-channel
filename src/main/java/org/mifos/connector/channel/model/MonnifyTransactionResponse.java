package org.mifos.connector.channel.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * DTO for Monnify transaction response.
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MonnifyTransactionResponse {
    private String message;
    private Map<String, String> errors;
    private String transactionReference;

}
