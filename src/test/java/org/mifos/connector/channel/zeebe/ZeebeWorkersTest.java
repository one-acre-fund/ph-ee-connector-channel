package org.mifos.connector.channel.zeebe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mifos.connector.channel.camel.config.CamelProperties.BATCH_ID;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.EXTERNAL_ID;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.SAMPLED_TX_IDS;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.TRANSACTION_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep2;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Opens the workers against a mocked Zeebe client and checks that every one of them goes
 * through the shutdown drain (FD-1939).
 */
class ZeebeWorkersTest {

    private static final int WORKERS = 9;

    private final ZeebeClient zeebeClient = mock(ZeebeClient.class, RETURNS_DEEP_STUBS);
    private final ProducerTemplate producerTemplate = mock(ProducerTemplate.class);
    private final JobWorker jobWorker = mock(JobWorker.class);
    private final Map<String, JobHandler> handlers = new LinkedHashMap<>();
    private final ZeebeWorkerDrain drain =
            new ZeebeWorkerDrain(Duration.ofSeconds(1), Duration.ofSeconds(1));
    private ZeebeWorkers zeebeWorkers;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        JobWorkerBuilderStep1 step1 = mock(JobWorkerBuilderStep1.class);
        JobWorkerBuilderStep2 step2 = mock(JobWorkerBuilderStep2.class);
        JobWorkerBuilderStep3 step3 = mock(JobWorkerBuilderStep3.class);
        String[] jobType = new String[1];
        when(zeebeClient.newWorker()).thenReturn(step1);
        when(step1.jobType(anyString())).thenAnswer(inv -> {
            jobType[0] = inv.getArgument(0);
            return step2;
        });
        when(step2.handler(any(JobHandler.class))).thenAnswer(inv -> {
            handlers.put(jobType[0], inv.getArgument(0));
            return step3;
        });
        when(step3.name(anyString())).thenReturn(step3);
        when(step3.maxJobsActive(anyInt())).thenReturn(step3);
        when(step3.open()).thenReturn(jobWorker);

        zeebeWorkers = new ZeebeWorkers();
        ReflectionTestUtils.setField(zeebeWorkers, "zeebeClient", zeebeClient);
        ReflectionTestUtils.setField(zeebeWorkers, "drain", drain);
        ReflectionTestUtils.setField(zeebeWorkers, "producerTemplate", producerTemplate);
        ReflectionTestUtils.setField(zeebeWorkers, "camelContext", new DefaultCamelContext());
        ReflectionTestUtils.setField(zeebeWorkers, "workerMaxJobs", 5);
    }

    @Test
    void everyWorkerIsRegisteredWithTheDrainAndClosedOnShutdown() {
        zeebeWorkers.setupWorkers();

        assertEquals(WORKERS, handlers.size());
        drain.onApplicationEvent(
                new ContextClosedEvent(mock(ConfigurableApplicationContext.class)));
        verify(jobWorker, times(WORKERS)).close();
    }

    @Test
    void everyHandlerRunsThroughTheDrainAndCompletesItsJob() throws Exception {
        zeebeWorkers.setupWorkers();

        for (Map.Entry<String, JobHandler> entry : handlers.entrySet()) {
            JobClient client = mock(JobClient.class, RETURNS_DEEP_STUBS);
            ActivatedJob job = mock(ActivatedJob.class);
            when(job.getKey()).thenReturn(7L);
            when(job.getType()).thenReturn(entry.getKey());
            when(job.getVariablesAsMap()).thenReturn(variables());

            entry.getValue().handle(client, job);

            verify(client).newCompleteCommand(7L);
            assertEquals(0, drain.inFlight(), entry.getKey() + " left a job in flight");
        }
        verify(producerTemplate).send(eq("direct:invoke-ack-workers"), any(Exchange.class));
        verify(producerTemplate).send(eq("direct:delete-webhook-log"), any(Exchange.class));
    }

    @Test
    void handlersHandBackJobsThatArriveAfterShutdown() throws Exception {
        zeebeWorkers.setupWorkers();
        drain.onApplicationEvent(
                new ContextClosedEvent(mock(ConfigurableApplicationContext.class)));
        JobClient client = mock(JobClient.class, RETURNS_DEEP_STUBS);
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getKey()).thenReturn(8L);
        when(job.getRetries()).thenReturn(2);

        handlers.get("send-success-to-channel").handle(client, job);

        verify(client).newFailCommand(8L);
        verify(client, times(0)).newCompleteCommand(anyLong());
    }

    private static Map<String, Object> variables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put(TRANSACTION_ID, "tx-1");
        variables.put(SAMPLED_TX_IDS, "[\"tx-1\"]");
        variables.put(BATCH_ID, "batch-1");
        variables.put(EXTERNAL_ID, "ext-1");
        return variables;
    }
}
