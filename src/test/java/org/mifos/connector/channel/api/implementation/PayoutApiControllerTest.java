package org.mifos.connector.channel.api.implementation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mifos.connector.channel.model.PayoutRequestDTO;
import org.mifos.connector.channel.payout.PayoutRejectedException;
import org.mifos.connector.channel.payout.PayoutService;
import org.mifos.connector.channel.payout.PayoutStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PayoutApiControllerTest {

    private static final String BODY = "{\"payee\":[{\"key\":\"MSISDN\",\"value\":\"254712345678\"},"
            + "{\"key\":\"FINERACTACCOUNTID\",\"value\":\"ACC-1\"}],"
            + "\"amount\":{\"amount\":\"1500\",\"currency\":\"KES\"},\"remarks\":\"Refund\"}";

    private final PayoutService payoutService = mock(PayoutService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PayoutApiController(payoutService)).build();
    }

    @Test
    void payout_started_shouldAnswerAcceptedWithTheTransactionId() throws Exception {
        when(payoutService.startPayout(eq("kenya"), eq("corr-1"), eq("momo"), any(PayoutRequestDTO.class)))
                .thenReturn("tx-1");

        mockMvc.perform(post("/channel/payout")
                        .header("Platform-TenantId", "kenya")
                        .header("X-CorrelationID", "corr-1")
                        .header("X-Payment-Scheme", "momo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transactionId").value("tx-1"))
                .andExpect(jsonPath("$.status").value("STARTED"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void payout_unconfirmedOrDuplicate_shouldAnswerWithTheTransactionIdToCheck() throws Exception {
        when(payoutService.startPayout(any(), any(), any(), any()))
                .thenThrow(new PayoutRejectedException(HttpStatus.SERVICE_UNAVAILABLE, PayoutStatus.UNCONFIRMED,
                        "tx-1", "Payout start not confirmed", "Check transaction tx-1 before retrying"))
                .thenThrow(new PayoutRejectedException(HttpStatus.CONFLICT, PayoutStatus.DUPLICATE, "tx-1",
                        "Payout already submitted", "nothing new was started"));

        for (var expected : new Object[][] {{503, "UNCONFIRMED"}, {409, "DUPLICATE"}}) {
            mockMvc.perform(post("/channel/payout")
                            .header("Platform-TenantId", "kenya")
                            .header("X-CorrelationID", "corr-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().is((int) expected[0]))
                    .andExpect(jsonPath("$.transactionId").value("tx-1"))
                    .andExpect(jsonPath("$.status").value(expected[1]));
        }
    }

    @Test
    void payout_withoutPaymentSchemeHeader_shouldLetTheServiceResolveIt() throws Exception {
        when(payoutService.startPayout(eq("kenya"), eq("corr-1"), isNull(), any(PayoutRequestDTO.class)))
                .thenReturn("tx-1");

        mockMvc.perform(post("/channel/payout")
                        .header("Platform-TenantId", "kenya")
                        .header("X-CorrelationID", "corr-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isAccepted());

        verify(payoutService).startPayout(eq("kenya"), eq("corr-1"), isNull(), any(PayoutRequestDTO.class));
    }

    @Test
    void payout_rejected_shouldAnswerTheRejectionStatusAndReason() throws Exception {
        when(payoutService.startPayout(any(), any(), any(), any())).thenThrow(
                new PayoutRejectedException(HttpStatus.NOT_FOUND, "Zeebe workflow not found", "NOT_FOUND"));

        mockMvc.perform(post("/channel/payout")
                        .header("Platform-TenantId", "kenya")
                        .header("X-CorrelationID", "corr-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Zeebe workflow not found"))
                .andExpect(jsonPath("$.details").value("NOT_FOUND"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.transactionId").doesNotExist());
    }

    @Test
    void payout_withoutCorrelationId_shouldBeRejectedBeforeTheService() throws Exception {
        mockMvc.perform(post("/channel/payout")
                        .header("Platform-TenantId", "kenya")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(payoutService);
    }
}
