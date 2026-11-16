package org.mifos.connector.channel.api.implementation;

import lombok.RequiredArgsConstructor;
import org.mifos.connector.channel.api.definition.PayoutApi;
import org.mifos.connector.channel.model.PayoutRequestDTO;
import org.mifos.connector.channel.model.PayoutResponseDTO;
import org.mifos.connector.channel.payout.PayoutRejectedException;
import org.mifos.connector.channel.payout.PayoutService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PayoutApiController implements PayoutApi {

    private final PayoutService payoutService;

    @Override
    public ResponseEntity<PayoutResponseDTO> payout(String tenant, String correlationId, String paymentScheme,
                                                    PayoutRequestDTO request) {
        var transactionId = payoutService.startPayout(tenant, correlationId, paymentScheme, request);
        return ResponseEntity.accepted().body(PayoutResponseDTO.started(transactionId));
    }

    @ExceptionHandler(PayoutRejectedException.class)
    ResponseEntity<PayoutResponseDTO> rejected(PayoutRejectedException e) {
        return ResponseEntity.status(e.getStatus()).body(PayoutResponseDTO.rejected(e.getMessage(), e.getDetails()));
    }
}
