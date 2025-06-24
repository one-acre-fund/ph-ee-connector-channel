package org.mifos.connector.channel.api.definition;

import org.mifos.connector.channel.model.SquadTransactionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import static org.mifos.connector.channel.camel.config.CamelProperties.SQUAD_SIGNATURE_HEADER;

public interface SquadTransactionsApi {

    @PostMapping("/squad/transactions")
    ResponseEntity<SquadTransactionResponse> processTransaction(@RequestHeader(value = SQUAD_SIGNATURE_HEADER) String signature,
                                                        @RequestBody String body);
}
