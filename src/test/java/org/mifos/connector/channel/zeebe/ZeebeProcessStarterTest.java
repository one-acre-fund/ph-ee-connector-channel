package org.mifos.connector.channel.zeebe;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep2;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ZeebeProcessStarterTest {

    @Test
    @SuppressWarnings("unchecked")
    void startZeebeWorkflowWithTransactionId_shouldStartTheLatestVersionWithTheGivenId() {
        var zeebeClient = mock(ZeebeClient.class);
        var step1 = mock(CreateProcessInstanceCommandStep1.class);
        var step2 = mock(CreateProcessInstanceCommandStep2.class);
        var step3 = mock(CreateProcessInstanceCommandStep3.class);
        ZeebeFuture<ProcessInstanceEvent> future = mock(ZeebeFuture.class);
        var instance = mock(ProcessInstanceEvent.class);
        when(zeebeClient.newCreateInstanceCommand()).thenReturn(step1);
        when(step1.bpmnProcessId("mpesa_payout_fineract-kenya")).thenReturn(step2);
        when(step2.latestVersion()).thenReturn(step3);
        when(step3.variables(org.mockito.ArgumentMatchers.<Map<String, Object>>any())).thenReturn(step3);
        when(step3.send()).thenReturn(future);
        when(future.join()).thenReturn(instance);
        when(instance.getProcessInstanceKey()).thenReturn(42L);

        var starter = new ZeebeProcessStarter();
        ReflectionTestUtils.setField(starter, "zeebeClient", zeebeClient);
        Map<String, Object> variables = new HashMap<>();
        variables.put(ZeebeVariables.CLIENT_CORRELATION_ID, "corr-1");

        var transactionId = starter.startZeebeWorkflowWithTransactionId("mpesa_payout_fineract-kenya", "tx-1",
                variables);

        assertEquals("tx-1", transactionId);
        assertEquals("tx-1", variables.get(ZeebeVariables.TRANSACTION_ID));
        assertTrue(variables.get(ZeebeVariables.ORIGIN_DATE) instanceof Long);
        verify(step3).variables(variables);
    }
}
