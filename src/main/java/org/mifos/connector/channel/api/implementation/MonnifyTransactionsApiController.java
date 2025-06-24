package org.mifos.connector.channel.api.implementation;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.mifos.connector.channel.api.definition.MonnifyTransactionsApi;
import org.mifos.connector.channel.model.MonnifyTransactionResponse;
import org.mifos.connector.channel.utils.Headers;
import org.mifos.connector.channel.utils.SpringWrapperUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import static org.mifos.connector.channel.camel.config.CamelProperties.MONNIFY_SIGNATURE_HEADER;

@RestController
public class MonnifyTransactionsApiController implements MonnifyTransactionsApi {

    private final ProducerTemplate producerTemplate;

    public MonnifyTransactionsApiController(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    @Override
    public ResponseEntity<MonnifyTransactionResponse> processTransaction(String signature, String body) {
        Exchange exchange = SpringWrapperUtil.getDefaultWrappedExchange(producerTemplate.getCamelContext(),
            new Headers.HeaderBuilder()
                .addHeader(MONNIFY_SIGNATURE_HEADER, signature)
                .build(), body);
        producerTemplate.send("direct:monnify-transaction-base", exchange);
        int statusCode = exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        MonnifyTransactionResponse response = exchange.getIn().getBody(MonnifyTransactionResponse.class);
        return ResponseEntity.status(statusCode).body(response);
    }
}
