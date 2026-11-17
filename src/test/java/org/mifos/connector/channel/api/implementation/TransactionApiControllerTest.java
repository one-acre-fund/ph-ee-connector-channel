package org.mifos.connector.channel.api.implementation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.TRANSACTION_ID;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionApiControllerTest {

    @Test
    void transactionResolve_shouldPassThePathTransactionIdAndTheResolutionToTheRoute() throws Exception {
        var producerTemplate = mock(ProducerTemplate.class);
        when(producerTemplate.getCamelContext()).thenReturn(new DefaultCamelContext());
        var controller = new TransactionApiController();
        ReflectionTestUtils.setField(controller, "producerTemplate", producerTemplate);
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());

        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(post("/channel/transaction/tx-1/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payoutFailed\":false,\"externalId\":\"NLJ41HAY6Q\"}"))
                .andExpect(status().isOk());

        var exchange = ArgumentCaptor.forClass(Exchange.class);
        verify(producerTemplate).send(eq("direct:post-transaction-resolve"), exchange.capture());
        assertEquals("tx-1", exchange.getValue().getIn().getHeader(TRANSACTION_ID));
        assertEquals("{\"payoutFailed\":false,\"externalId\":\"NLJ41HAY6Q\"}",
                exchange.getValue().getIn().getBody(String.class));
    }
}
