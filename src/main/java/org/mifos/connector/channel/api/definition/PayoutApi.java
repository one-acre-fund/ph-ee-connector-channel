package org.mifos.connector.channel.api.definition;

import org.mifos.connector.channel.model.PayoutRequestDTO;
import org.mifos.connector.channel.model.PayoutResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import static org.mifos.connector.channel.camel.config.CamelProperties.PAYMENT_SCHEME_HEADER;

public interface PayoutApi {

    /**
     * Starts a payout to a customer's mobile money wallet on any scheme. Always asynchronous: answers 202 with the
     * transaction id. The same X-CorrelationID for the same tenant always gives the same transaction id, and the
     * provider never pays the same transaction id twice.
     */
    @PostMapping("/channel/payout")
    ResponseEntity<PayoutResponseDTO> payout(@RequestHeader("Platform-TenantId") String tenant,
                                             @RequestHeader("X-CorrelationID") String correlationId,
                                             @RequestHeader(value = PAYMENT_SCHEME_HEADER, required = false)
                                             String paymentScheme,
                                             @RequestBody PayoutRequestDTO request);
}
