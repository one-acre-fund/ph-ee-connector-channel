package org.mifos.connector.channel.api.implementation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultExchange;
import org.mifos.connector.channel.api.definition.SquadTransactionsApi;
import org.mifos.connector.channel.model.SquadTransactionResponse;
import org.mifos.connector.channel.model.SquadTransactionResponseList;
import org.mifos.connector.channel.model.SquadTransactionsSyncRequest;
import org.mifos.connector.channel.service.SquadTransactionService;
import org.mifos.connector.channel.utils.Headers;
import org.mifos.connector.channel.utils.SpringWrapperUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.mifos.connector.channel.camel.config.CamelProperties.SQUAD_SIGNATURE_HEADER;

/**
 * Controller for handling Squad transactions.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
public class SquadTransactionsApiController implements SquadTransactionsApi {

    private final ProducerTemplate producerTemplate;
    private final SquadTransactionService squadTransactionService;


    @Override
    public ResponseEntity<SquadTransactionResponse> processTransaction(String signature, String body) {
        Headers headers = new Headers.HeaderBuilder()
            .addHeader(SQUAD_SIGNATURE_HEADER, signature)
            .build();
        Exchange exchange = SpringWrapperUtil.getDefaultWrappedExchange(producerTemplate.getCamelContext(),
            headers, body);
        producerTemplate.send("direct:squad-transaction-base", exchange);
        int statusCode = exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        SquadTransactionResponse response = exchange.getIn().getBody(SquadTransactionResponse.class);
        return ResponseEntity.status(statusCode).body(response);
    }

    @Override
    public ResponseEntity<SquadTransactionResponseList> syncTransactions(SquadTransactionsSyncRequest body) {
        return ResponseEntity.status(HttpStatus.OK).body(this.squadTransactionService.syncTransactions(body));
    }


}
