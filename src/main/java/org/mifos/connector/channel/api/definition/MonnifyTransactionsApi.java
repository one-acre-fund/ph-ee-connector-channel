package org.mifos.connector.channel.api.definition;

import org.mifos.connector.channel.model.MonnifyTransactionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import static org.mifos.connector.channel.camel.config.CamelProperties.MONNIFY_SIGNATURE_HEADER;

/**
 * API definition for processing Monnify transactions.
 */
public interface MonnifyTransactionsApi {

    /**
     * Processes a Monnify transaction.
     *
     * @param signature SHA 512 HMAC signature of the request body, generated using the secret key.
     * @param body      JSON body of the request containing monnify transaction details.
     * @return {@link ResponseEntity} containing {@link MonnifyTransactionResponse}.
     */
    @PostMapping("/monnify/transactions")
    ResponseEntity<MonnifyTransactionResponse> processTransaction(@RequestHeader(value = MONNIFY_SIGNATURE_HEADER) String signature,
                                                                  @RequestBody String body);
}
