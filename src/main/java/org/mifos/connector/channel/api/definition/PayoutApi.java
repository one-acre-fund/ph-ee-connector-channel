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
     * Starts a payout to a customer's mobile money wallet on any scheme. Always asynchronous. The same X-CorrelationID
     * for the same tenant always gives the same transaction id, and that transaction id is claimed in Redis before the
     * workflow starts, so a resent request never starts a second payout (for {@code payout.claim-ttl}, 30 days).
     *
     * <ul>
     *   <li>202 {@code STARTED}: the payout workflow was started</li>
     *   <li>409 {@code DUPLICATE}: already submitted; nothing new was started</li>
     *   <li>503 {@code UNCONFIRMED}: the start was not confirmed; check the transaction id before retrying</li>
     *   <li>400/403/404/503 {@code REJECTED}: nothing was started</li>
     * </ul>
     */
    @PostMapping("/channel/payout")
    ResponseEntity<PayoutResponseDTO> payout(@RequestHeader("Platform-TenantId") String tenant,
                                             @RequestHeader("X-CorrelationID") String correlationId,
                                             @RequestHeader(value = PAYMENT_SCHEME_HEADER, required = false)
                                             String paymentScheme,
                                             @RequestBody PayoutRequestDTO request);
}
