package org.mifos.connector.channel.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * DTO for Squad transaction response.
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SquadTransactionResponse {
    @JsonProperty("response_code")
    private int responseCode;

    @JsonProperty("transaction_reference")
    private String transactionReference;

    @JsonProperty("response_description")
    private String responseDescription;

    private Map<String, String> errors;
}
