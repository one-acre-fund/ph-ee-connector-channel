package org.mifos.connector.channel.payout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mifos.connector.channel.utils.AMSProps;
import org.mifos.connector.channel.utils.AMSUtils;
import org.mifos.connector.channel.zeebe.ZeebeProcessStarter;
import org.mifos.connector.common.gsma.dto.CustomData;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mifos.connector.channel.payout.PayoutTestData.request;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayoutServiceTest {

    private static final String TENANT = "kenya";
    private static final String CORRELATION_ID = "corr-1";

    private final ZeebeProcessStarter zeebeProcessStarter = mock(ZeebeProcessStarter.class);
    private final AMSUtils amsUtils = new AMSUtils();
    private final AMSProps amsProps = new AMSProps();
    private final PayoutProperties properties = new PayoutProperties();
    private final PayoutClaims claims = mock(PayoutClaims.class);
    private PayoutService service;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        when(claims.claim(anyString())).thenReturn(true);
        amsProps.setGroups(List.of(ams("FINERACTACCOUNTID", "fineract"), ams("default", "odoo")));
        ReflectionTestUtils.setField(amsUtils, "amsProps", amsProps);
        ReflectionTestUtils.setField(amsUtils, "objectMapper", new ObjectMapper());
        var resolver = new PaymentSchemeResolver(Map.of("safaricom", "mpesa", "mtn", "momo"));
        service = new PayoutService(properties, resolver, zeebeProcessStarter, amsUtils, new ObjectMapper(),
                claims, List.of(TENANT, "uganda"), true, false);
    }

    @Test
    void startPayout_shouldStartTheSchemeFlowWithTheContractVariables() {
        var transactionId = service.startPayout(TENANT, CORRELATION_ID, null, request("254712345678", "1500"));

        assertEquals(PayoutRequestValidator.transactionId(TENANT, CORRELATION_ID), transactionId);
        var variables = startedVariables("mpesa_payout_fineract-kenya", transactionId);
        assertEquals("mpesa", variables.get("paymentScheme"));
        assertEquals("fineract", variables.get("ams"));
        assertEquals("254712345678", variables.get("phoneNumber"));
        assertEquals("ACC-1", variables.get("accountId"));
        assertEquals("1500", variables.get("amount"));
        assertEquals("KES", variables.get("currency"));
        assertEquals("Refund of overpayment", variables.get("remarks"));
        assertEquals("PT2M", variables.get("payoutTimer"));
        assertTrue(((String) variables.get("payoutRequest")).contains("\"remarks\":\"Refund of overpayment\""));
    }

    @Test
    void startPayout_mtnNumber_shouldStartTheMomoFlow() {
        var request = request("256772123456", "5000");
        request.getAmount().setCurrency("UGX");
        request.setRemarks(null);

        var transactionId = service.startPayout("uganda", CORRELATION_ID, null, request);

        var variables = startedVariables("momo_payout_fineract-uganda", transactionId);
        assertEquals("", variables.get("remarks"));
        assertEquals("UGX", variables.get("currency"));
    }

    @Test
    void startPayout_unknownAccountKey_shouldUseTheDefaultAms() {
        var request = request("254712345678", "100");
        request.getPayee().get(1).setKey("ODOOACCOUNTID");

        var transactionId = service.startPayout(TENANT, CORRELATION_ID, "MPESA", request);

        startedVariables("mpesa_payout_odoo-kenya", transactionId);
    }

    @Test
    void startPayout_withCallbackUrl_shouldPassIt() {
        var request = request("254712345678", "100");
        var callback = new CustomData();
        callback.setKey("callbackUrl");
        callback.setValue("https://example.org/callback");
        request.setCustomData(List.of(callback));

        var transactionId = service.startPayout(TENANT, CORRELATION_ID, null, request);

        assertEquals("https://example.org/callback",
                startedVariables("mpesa_payout_fineract-kenya", transactionId).get("callbackUrl"));
    }

    @Test
    void startPayout_invalidCallbackUrl_shouldReject() {
        var request = request("254712345678", "100");
        var callback = new CustomData();
        callback.setKey("callbackUrl");
        callback.setValue("ftp://example.org/callback");
        request.setCustomData(List.of(callback));

        var e = assertRejected(HttpStatus.BAD_REQUEST, () -> service.startPayout(TENANT, CORRELATION_ID, null,
                request));
        assertTrue(e.getDetails().contains("callbackUrl"), e.getDetails());
    }

    @Test
    void startPayout_nullTenant_shouldReject() {
        assertRejected(HttpStatus.BAD_REQUEST, () -> service.startPayout(null, CORRELATION_ID, null,
                request("254712345678", "100")));
    }

    @Test
    void startPayout_unserializableRequest_shouldFailWithoutStarting() throws Exception {
        var objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("boom") { });
        var failing = new PayoutService(properties, new PaymentSchemeResolver(Map.of()), zeebeProcessStarter,
                amsUtils, objectMapper, claims, List.of(TENANT), true, false);

        assertThrows(UncheckedIOException.class, () -> failing.startPayout(TENANT, CORRELATION_ID, null,
                request("254712345678", "100")));
        verify(zeebeProcessStarter, never()).startZeebeWorkflowWithTransactionId(anyString(), anyString(), anyMap());
    }

    @Test
    void startPayout_whenDisabled_shouldRejectWithForbidden() {
        properties.setEnabled(false);
        assertRejected(HttpStatus.FORBIDDEN, () -> service.startPayout(TENANT, CORRELATION_ID, null,
                request("254712345678", "100")));
    }

    @Test
    void startPayout_unknownTenant_shouldReject() {
        assertRejected(HttpStatus.BAD_REQUEST, () -> service.startPayout("rwanda", CORRELATION_ID, null,
                request("254712345678", "100")));
    }

    @Test
    void startPayout_blankCorrelationId_shouldReject() {
        assertRejected(HttpStatus.BAD_REQUEST, () -> service.startPayout(TENANT, " ", null,
                request("254712345678", "100")));
    }

    @Test
    void startPayout_invalidRequest_shouldRejectWithTheReason() {
        var e = assertRejected(HttpStatus.BAD_REQUEST, () -> service.startPayout(TENANT, CORRELATION_ID, null,
                request("254712345678", "-1")));
        assertTrue(e.getDetails().contains("Amount"), e.getDetails());
    }

    @Test
    void startPayout_noAmsConfigured_shouldReject() {
        amsProps.setGroups(List.of());
        assertRejected(HttpStatus.BAD_REQUEST, () -> service.startPayout(TENANT, CORRELATION_ID, null,
                request("254712345678", "100")));
    }

    @Test
    void startPayout_started_shouldClaimFirstAndMarkTheClaimStarted() {
        var transactionId = service.startPayout(TENANT, CORRELATION_ID, null, request("254712345678", "100"));

        var order = inOrder(claims, zeebeProcessStarter);
        order.verify(claims).claim(transactionId);
        order.verify(zeebeProcessStarter).startZeebeWorkflowWithTransactionId(anyString(), eq(transactionId),
                anyMap());
        order.verify(claims).update(transactionId, PayoutClaims.State.STARTED);
    }

    @Test
    void startPayout_resent_shouldAnswerDuplicateWithTheTransactionIdAndNotStart() {
        var transactionId = PayoutRequestValidator.transactionId(TENANT, CORRELATION_ID);
        when(claims.claim(transactionId)).thenReturn(false);
        when(claims.state(transactionId)).thenReturn(Optional.of(PayoutClaims.State.STARTED));

        var e = assertRejected(HttpStatus.CONFLICT, () -> service.startPayout(TENANT, CORRELATION_ID, null,
                request("254712345678", "100")));

        assertEquals(PayoutStatus.DUPLICATE, e.getOutcome());
        assertEquals(transactionId, e.getTransactionId());
        assertTrue(e.getDetails().contains("already started"), e.getDetails());
    }

    @Test
    void startPayout_resentAfterAnUnconfirmedStart_shouldSayToCheckTheTransaction() {
        when(claims.claim(anyString())).thenReturn(false);
        when(claims.state(anyString())).thenReturn(Optional.of(PayoutClaims.State.UNCONFIRMED));

        var e = assertRejected(HttpStatus.CONFLICT, () -> service.startPayout(TENANT, CORRELATION_ID, null,
                request("254712345678", "100")));

        assertEquals(PayoutStatus.DUPLICATE, e.getOutcome());
        assertTrue(e.getDetails().contains("not confirmed"), e.getDetails());
    }

    @Test
    void startPayout_resentWhileTheClaimStateIsUnknown_shouldStillAnswerDuplicate() {
        when(claims.claim(anyString())).thenReturn(false);
        when(claims.state(anyString())).thenReturn(Optional.empty());

        var e = assertRejected(HttpStatus.CONFLICT, () -> service.startPayout(TENANT, CORRELATION_ID, null,
                request("254712345678", "100")));

        assertEquals(PayoutStatus.DUPLICATE, e.getOutcome());
    }

    @Test
    void startPayout_redisUnreachable_shouldRefuseWithoutStarting() {
        when(claims.claim(anyString())).thenThrow(new RedisConnectionFailureException("Connection refused"));

        var e = assertRejected(HttpStatus.SERVICE_UNAVAILABLE, () -> service.startPayout(TENANT, CORRELATION_ID,
                null, request("254712345678", "100")), false);

        assertEquals(PayoutStatus.REJECTED, e.getOutcome());
        assertNull(e.getTransactionId());
    }

    @Test
    void startPayout_flowNotDeployed_shouldAnswerNotFoundAndReleaseTheClaim() {
        when(zeebeProcessStarter.startZeebeWorkflowWithTransactionId(anyString(), anyString(), anyMap()))
                .thenThrow(new ClientStatusException(Status.NOT_FOUND, null));

        var e = assertRejected(HttpStatus.NOT_FOUND, () -> service.startPayout(TENANT, CORRELATION_ID, null,
                request("254712345678", "100")), true);

        assertEquals(PayoutStatus.REJECTED, e.getOutcome());
        verify(claims).release(PayoutRequestValidator.transactionId(TENANT, CORRELATION_ID));
    }

    @Test
    void startPayout_flowNotDeployedAndReleaseFails_shouldStillAnswerNotFound() {
        when(zeebeProcessStarter.startZeebeWorkflowWithTransactionId(anyString(), anyString(), anyMap()))
                .thenThrow(new ClientStatusException(Status.NOT_FOUND, null));
        doThrow(new RedisConnectionFailureException("gone")).when(claims).release(anyString());

        assertRejected(HttpStatus.NOT_FOUND, () -> service.startPayout(TENANT, CORRELATION_ID, null,
                request("254712345678", "100")), true);
    }

    @Test
    void startPayout_zeebeUnavailable_shouldBeUnconfirmedWithTheTransactionIdAndKeepTheClaim() {
        when(zeebeProcessStarter.startZeebeWorkflowWithTransactionId(anyString(), anyString(), anyMap()))
                .thenThrow(new ClientStatusException(Status.UNAVAILABLE, null));
        var transactionId = PayoutRequestValidator.transactionId(TENANT, CORRELATION_ID);

        var e = assertRejected(HttpStatus.SERVICE_UNAVAILABLE, () -> service.startPayout(TENANT, CORRELATION_ID,
                null, request("254712345678", "100")), true);

        assertEquals(PayoutStatus.UNCONFIRMED, e.getOutcome());
        assertEquals(transactionId, e.getTransactionId());
        assertTrue(e.getDetails().contains("Check transaction " + transactionId), e.getDetails());
        verify(claims).update(transactionId, PayoutClaims.State.UNCONFIRMED);
        verify(claims, never()).release(anyString());
    }

    @Test
    void startPayout_lostAnswer_shouldBeUnconfirmed() {
        when(zeebeProcessStarter.startZeebeWorkflowWithTransactionId(anyString(), anyString(), anyMap()))
                .thenThrow(new ClientException("timed out waiting for the response"));
        doThrow(new RedisConnectionFailureException("gone")).when(claims).update(anyString(), any());

        var e = assertRejected(HttpStatus.SERVICE_UNAVAILABLE, () -> service.startPayout(TENANT, CORRELATION_ID,
                null, request("254712345678", "100")), true);

        assertEquals(PayoutStatus.UNCONFIRMED, e.getOutcome());
    }

    @Test
    void startPayout_startedButClaimUpdateFails_shouldStillReturnTheTransactionId() {
        doThrow(new RedisConnectionFailureException("gone")).when(claims).update(anyString(), any());

        assertEquals(PayoutRequestValidator.transactionId(TENANT, CORRELATION_ID),
                service.startPayout(TENANT, CORRELATION_ID, null, request("254712345678", "100")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> startedVariables(String workflow, String transactionId) {
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(zeebeProcessStarter).startZeebeWorkflowWithTransactionId(eq(workflow), eq(transactionId),
                variables.capture());
        return variables.getValue();
    }

    private PayoutRejectedException assertRejected(HttpStatus status, Runnable call) {
        return assertRejected(status, call, false);
    }

    private PayoutRejectedException assertRejected(HttpStatus status, Runnable call, boolean startAttempted) {
        var e = assertThrows(PayoutRejectedException.class, call::run);
        assertEquals(status, e.getStatus());
        verify(zeebeProcessStarter, startAttempted ? times(1) : never())
                .startZeebeWorkflowWithTransactionId(anyString(), anyString(), anyMap());
        return e;
    }

    private static AMSProps.AMS ams(String identifier, String value) {
        var ams = new AMSProps.AMS();
        ams.setIdentifier(identifier);
        ams.setValue(value);
        return ams;
    }
}
