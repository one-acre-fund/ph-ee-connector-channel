package org.mifos.connector.channel;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mifos.connector.channel.camel.config.Client;
import org.mifos.connector.channel.camel.config.ClientProperties;
import org.mifos.connector.channel.camel.routes.ChannelRouteBuilder;
import org.mifos.connector.channel.camel.routes.TokenWithExpiry;
import org.mifos.connector.channel.operations.TokenCache;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChannelRouteBuilderTest {

    private ChannelRouteBuilder routeBuilder;
    private RestTemplate restTemplate;
    private ClientProperties clientProperties;
    private Client client;
    private final TokenCache tokenCache = new TokenCache();

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        clientProperties = mock(ClientProperties.class);
        client = mock(Client.class);
        when(client.getClientId()).thenReturn("testClientId");
        when(client.getClientSecret()).thenReturn("testClientSecret");

        routeBuilder = new ChannelRouteBuilder(
                Collections.singletonList("tenant1"),
                "paymentTransferFlow",
                "specialPaymentTransferFlow",
                "transactionRequestFlow",
                "partyRegistration",
                "mpesaFlow",
                "http://authhost",
                "http://opsurl",
                60L,
                "/transfers",
                "/transactionReq",
                true,
                true,
                "timer",
                "Basic testheader",
                null,
                null,
                null,
                null,
                null,
                clientProperties,
                restTemplate,
                tokenCache
        );
    }

    @Test
    void testBuildHttpEntity_TokenCached() {
        tokenCache.updateTenantCache("tenant1", new TokenWithExpiry("cachedToken", Instant.now().plusSeconds(600)));
        HttpEntity<?> entity = routeBuilder.buildHttpEntity("tenant1", client);
        assertTrue(entity.getHeaders().get("Authorization").get(0).contains("Bearer cachedToken"));
    }

    @Test
    void testBuildHttpEntity_TokenNotCached() {
        when(client.getClientId()).thenReturn("id");
        when(client.getClientSecret()).thenReturn("secret");

        ResponseEntity<String> responseEntity = new ResponseEntity<>(
                new JSONObject().put("access_token", "newToken").toString(),
                HttpStatus.OK
        );
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        HttpEntity<?> entity = routeBuilder.buildHttpEntity("tenant1", client);
        assertTrue(entity.getHeaders().get("Authorization").get(0).contains("Bearer newToken"));
        assertEquals("newToken", tokenCache.getTenantCache("tenant1").token);
    }

    @Test
    void testBuildHeaderAndBody_NoToken() {
        HttpEntity<MultiValueMap<String, String>> entity = routeBuilder.buildHeaderAndBody("tenant1", null, client);
        HttpHeaders headers = entity.getHeaders();
        assertEquals("tenant1", headers.getFirst("Platform-TenantId"));
        assertEquals("Basic testheader", headers.getFirst("Authorization"));
        MultiValueMap<String, String> body = entity.getBody();
        assertEquals("testClientId", body.getFirst("username"));
        assertEquals("testClientSecret", body.getFirst("password"));
        assertEquals("password", body.getFirst("grant_type"));
        assertEquals("paymenthub", body.getFirst("client_id"));
    }

    @Test
    void testBuildHeaderAndBody_WithToken() {
        HttpEntity<MultiValueMap<String, String>> entity = routeBuilder.buildHeaderAndBody("tenant1", "myToken", client);
        HttpHeaders headers = entity.getHeaders();
        assertEquals("tenant1", headers.getFirst("Platform-TenantId"));
        assertEquals("Bearer myToken", headers.getFirst("Authorization"));
        MultiValueMap<String, String> body = entity.getBody();
        assertEquals("testClientId", body.getFirst("username"));
        assertEquals("testClientSecret", body.getFirst("password"));
        assertEquals("password", body.getFirst("grant_type"));
        assertEquals("paymenthub", body.getFirst("client_id"));
    }
}