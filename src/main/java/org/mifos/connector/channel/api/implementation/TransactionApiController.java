package org.mifos.connector.channel.api.implementation;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.mifos.connector.channel.api.definition.TransactionApi;
import org.mifos.connector.channel.GSMA_API.*;
import org.mifos.connector.channel.utils.Headers;
import org.mifos.connector.channel.utils.SpringWrapperUtil;
import org.mifos.connector.common.channel.dto.TransactionChannelRequestDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import static org.mifos.connector.channel.camel.config.CamelProperties.CLIENTCORRELATIONID;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.TRANSACTION_ID;

@RestController
public class TransactionApiController implements TransactionApi {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    private ProducerTemplate producerTemplate;

    @Override
    public GsmaP2PResponseDto transaction(String tenant, String correlationId,TransactionChannelRequestDTO requestBody) throws JsonProcessingException {
        Headers headers = new Headers.HeaderBuilder()
                .addHeader("Platform-TenantId", tenant)
                .addHeader(CLIENTCORRELATIONID, correlationId)
                .build();
        Exchange exchange = SpringWrapperUtil.getDefaultWrappedExchange(producerTemplate.getCamelContext(),
                headers, objectMapper.writeValueAsString(requestBody));
        producerTemplate.send("direct:post-transaction-request", exchange);

        String body = exchange.getIn().getBody(String.class);
        return objectMapper.readValue(body, GsmaP2PResponseDto.class);
    }

    @Override
    public void transactionResolve(String transactionId, String requestBody) throws JsonProcessingException {
        // The route publishes the operator's resolution with the transaction id as the correlation key
        Headers headers = new Headers.HeaderBuilder()
                .addHeader(TRANSACTION_ID, transactionId)
                .build();
        Exchange exchange = SpringWrapperUtil.getDefaultWrappedExchange(producerTemplate.getCamelContext(),
                headers, requestBody);
        producerTemplate.send("direct:post-transaction-resolve", exchange);

    }
}
