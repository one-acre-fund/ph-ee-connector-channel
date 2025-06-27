package org.mifos.connector.channel.api.definition;

import org.mifos.connector.channel.model.SquadTransactionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import static org.mifos.connector.channel.camel.config.CamelProperties.SQUAD_SIGNATURE_HEADER;

/**
 * API definition for processing squad transactions.
 */
public interface SquadTransactionsApi {

    /**
     * Processes a Squad POS transaction.
     *
     * @param signature SHA 512 HMAC signature of the request body, generated using the secret key.
     * @param body      JSON body of the request containing squad transaction details.
     * @return {@link ResponseEntity} containing {@link SquadTransactionResponse}.
     */
    @PostMapping("/squad/transactions")
    ResponseEntity<SquadTransactionResponse> processTransaction(@RequestHeader(value = SQUAD_SIGNATURE_HEADER) String signature,
                                                        @RequestBody String body);
}
