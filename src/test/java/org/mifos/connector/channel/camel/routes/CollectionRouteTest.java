package org.mifos.connector.channel.camel.routes;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mifos.connector.channel.camel.config.ClientProperties;
import org.mifos.connector.channel.operations.TokenCache;
import org.mifos.connector.channel.utils.AMSUtils;
import org.mifos.connector.channel.zeebe.ZeebeProcessStarter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs the real {@code direct:post-collection} route to check that invalid requests are rejected before a
 * workflow is started.
 */
class CollectionRouteTest {

    private static final String TENANT = "tenant1";

    private final ZeebeProcessStarter zeebeProcessStarter = mock(ZeebeProcessStarter.class);
    private final AMSUtils amsUtils = mock(AMSUtils.class);
    private DefaultCamelContext camelContext;
    private ProducerTemplate producer;

    @BeforeEach
    void setUp() throws Exception {
        ChannelRouteBuilder routeBuilder = new ChannelRouteBuilder(
                Collections.singletonList(TENANT),
                "paymentTransferFlow",
                "specialPaymentTransferFlow",
                "transactionRequestFlow",
                "partyRegistration",
                "{ps}_flow_{ams}-{dfspid}",
                "http://authhost",
                "http://opsurl",
                60L,
                "/transfers",
                "/transactionReq",
                true,
                true,
                "PT1M",
                "Basic testheader",
                null,
                zeebeProcessStarter,
                null,
                null,
                null,
                mock(ClientProperties.class),
                mock(RestTemplate.class),
                new TokenCache(),
                30L
        );
        ReflectionTestUtils.setField(routeBuilder, "amsUtils", amsUtils);
        when(amsUtils.postConstruct()).thenReturn(Collections.emptyList());
        when(amsUtils.getOrDefault(any(), eq("customData"), any())).thenReturn("");

        camelContext = new DefaultCamelContext();
        camelContext.setAutoStartup(false);
        camelContext.addRoutes(routeBuilder);
        camelContext.start();
        camelContext.getRouteController().startRoute("mpesa-payment-request");
        producer = camelContext.createProducerTemplate();
    }

    @AfterEach
    void tearDown() {
        camelContext.stop();
    }

    @Test
    void invalidRequest_shouldReturn400WithoutStartingWorkflow() {
        Exchange response = post(request("+null", "100"));

        assertEquals(400, response.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
        JSONObject body = new JSONObject(response.getIn().getBody(String.class));
        assertEquals("Invalid collection request", body.getString("error"));
        assertEquals("Payer MSISDN '+null' is invalid, it must contain digits only", body.getString("details"));
        verify(zeebeProcessStarter, never()).startZeebePaymentWorkflow(anyString(), anyString(), anyMap());
    }

    @Test
    void missingAmount_shouldReturn400() {
        JSONObject request = request("254712345678", "100");
        request.remove("amount");

        Exchange response = post(request);

        assertEquals(400, response.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Amount is missing", new JSONObject(response.getIn().getBody(String.class)).getString("details"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void validRequest_shouldStartWorkflow() {
        when(zeebeProcessStarter.startZeebePaymentWorkflow(anyString(), anyString(), anyMap())).thenReturn("tx-1");

        Exchange response = post(request("254712345678", "100"));

        assertEquals(200, response.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("tx-1", new JSONObject(response.getIn().getBody(String.class)).getString("transactionId"));
        verify(zeebeProcessStarter).startZeebePaymentWorkflow(eq("mpesa_flow_value-" + TENANT), anyString(),
                (Map<String, Object>) any());
    }

    private Exchange post(JSONObject request) {
        return producer.request("direct:post-collection", exchange -> {
            exchange.getIn().setHeader("Platform-TenantId", TENANT);
            exchange.getIn().setHeader("X-CorrelationID", "corr-1");
            exchange.getIn().setHeader("X-Payment-Scheme", "mpesa");
            exchange.getIn().setBody(request.toString());
        });
    }

    private static JSONObject request(String msisdn, String amount) {
        return new JSONObject()
                .put("payer", new JSONArray()
                        .put(new JSONObject().put("key", "MSISDN").put("value", msisdn))
                        .put(new JSONObject().put("key", "ACCOUNTID").put("value", "123")))
                .put("amount", new JSONObject().put("amount", amount).put("currency", "KES"));
    }
}
