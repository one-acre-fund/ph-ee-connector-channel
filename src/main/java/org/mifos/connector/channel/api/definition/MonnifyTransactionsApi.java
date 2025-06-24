package org.mifos.connector.channel.api.definition;

import org.mifos.connector.channel.model.MonnifyTransactionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import static org.mifos.connector.channel.camel.config.CamelProperties.MONNIFY_SIGNATURE_HEADER;

public interface MonnifyTransactionsApi {

    @PostMapping("/monnify/transactions")
    ResponseEntity<MonnifyTransactionResponse> processTransaction(@RequestHeader(value = MONNIFY_SIGNATURE_HEADER) String signature,
                                                                  @RequestBody String body);
}
