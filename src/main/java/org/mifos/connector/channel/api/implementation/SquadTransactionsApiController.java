package org.mifos.connector.channel.api.implementation;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.mifos.connector.channel.api.definition.SquadTransactionsApi;
import org.mifos.connector.channel.model.SquadTransactionResponse;
import org.mifos.connector.channel.utils.Headers;
import org.mifos.connector.channel.utils.SpringWrapperUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import static org.mifos.connector.channel.camel.config.CamelProperties.SQUAD_SIGNATURE_HEADER;

@RestController
public class SquadTransactionsApiController implements SquadTransactionsApi {

    private final ProducerTemplate producerTemplate;

    public SquadTransactionsApiController(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

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
}
