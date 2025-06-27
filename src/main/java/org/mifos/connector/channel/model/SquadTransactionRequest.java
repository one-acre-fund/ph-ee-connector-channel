package org.mifos.connector.channel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * DTO for Squad transaction request.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SquadTransactionRequest {
    @NotBlank
    @JsonProperty("transaction_reference")
    private String transactionReference;

    @NotBlank
    @JsonProperty("virtual_account_number")
    private String virtualAccountNumber;

    @NotNull
    @Positive
    @JsonProperty("principal_amount")
    private BigDecimal principalAmount;

    @JsonProperty("settled_amount")
    private BigDecimal settledAmount;

    @JsonProperty("fee_charged")
    private BigDecimal feeCharged;

    @JsonProperty("transaction_date")
    private String transactionDate;

    @NotBlank
    @JsonProperty("customer_identifier")
    private String customerIdentifier;

    @JsonProperty("transaction_identifier")
    private String transactionIdentifier;
    private String remarks;
    private String currency;
    private String channel;

    @JsonProperty("sender_name")
    private String senderName;
    private Meta meta;

    @JsonProperty("encrypted_body")
    private String encryptedBody;

    @Getter
    @Setter
    public static class Meta {

        @JsonProperty("freeze_transaction_ref")
        private String freezeTransactionRef;

        @JsonProperty("reason_for_frozen_transaction")
        private String reasonForFrozenTransaction;
    }
}
