package org.mifos.connector.channel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * DTO for Monnify transaction request.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MonnifyTransactionRequest {
    private EventData eventData;
    private String eventType;

    @Getter
    @Setter
    public static class EventData {
        private Product product;

        @NotBlank
        private String transactionReference;
        private String paymentReference;
        private String paidOn;
        private String paymentDescription;

        @NotNull
        private BigDecimal amountPaid;
        private BigDecimal totalPayable;
        private OfflineProductInformation offlineProductInformation;
        private String paymentMethod;
        private String currency;
        private BigDecimal settlementAmount;
        private String paymentStatus;

        @NotNull
        private Customer customer;
    }

    @Getter
    @Setter
    public static class Product {
        private String reference;
        private String type;
    }

    @Getter
    @Setter
    public static class Customer {
        private String name;

        @NotBlank
        private String email;
    }

    @Getter
    @Setter
    public static class OfflineProductInformation {
        private String code;
        private String type;
    }
}
