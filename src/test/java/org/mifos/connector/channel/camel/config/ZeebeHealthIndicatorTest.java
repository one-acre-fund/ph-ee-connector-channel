package org.mifos.connector.channel.camel.config;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.TopologyRequestStep1;
import io.camunda.zeebe.client.api.response.BrokerInfo;
import io.camunda.zeebe.client.api.response.Topology;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZeebeHealthIndicatorTest {

    @Mock
    private ZeebeClient zeebeClient;

    @Mock
    private TopologyRequestStep1 topologyRequestStep1;

    @Mock
    private ZeebeFuture<Topology> zeebeFuture;

    @Mock
    private Topology topology;

    @Mock
    private BrokerInfo brokerInfo;

    private ZeebeHealthIndicator healthIndicator;

    private static final long CACHE_DURATION_MS = 15000;
    private static final long HEALTH_CHECK_TIMEOUT_SECONDS = 5;

    @BeforeEach
    void setUp() {
        healthIndicator = new ZeebeHealthIndicator(zeebeClient);
        ReflectionTestUtils.setField(healthIndicator, "cacheDurationMs", CACHE_DURATION_MS);
        ReflectionTestUtils.setField(healthIndicator, "healthCheckTimeoutSeconds", HEALTH_CHECK_TIMEOUT_SECONDS);
    }

    @Test
    void health_shouldReturnUp_whenZeebeIsHealthy() throws Exception {
        // Given
        when(zeebeClient.newTopologyRequest()).thenReturn(topologyRequestStep1);
        when(topologyRequestStep1.send()).thenReturn(zeebeFuture);
        when(zeebeFuture.get(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(topology);
        when(topology.getBrokers()).thenReturn(Collections.singletonList(brokerInfo));
        when(topology.getClusterSize()).thenReturn(3);
        when(topology.getPartitionsCount()).thenReturn(6);

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("brokers", 1);
        assertThat(health.getDetails()).containsEntry("clusterSize", 3);
        assertThat(health.getDetails()).containsEntry("partitions", 6);

        verify(zeebeClient).newTopologyRequest();
        verify(topologyRequestStep1).send();
        verify(zeebeFuture).get(HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void health_shouldReturnDown_whenTopologyRequestTimesOut() throws Exception {
        // Given
        when(zeebeClient.newTopologyRequest()).thenReturn(topologyRequestStep1);
        when(topologyRequestStep1.send()).thenReturn(zeebeFuture);
        when(zeebeFuture.get(anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new TimeoutException("Timeout"));

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
        assertThat(health.getDetails().get("error").toString())
                .contains("timed out after " + HEALTH_CHECK_TIMEOUT_SECONDS + "s");

        verify(zeebeClient).newTopologyRequest();
    }

    @Test
    void health_shouldReturnDown_whenTopologyRequestThrowsException() throws Exception {
        // Given
        when(zeebeClient.newTopologyRequest()).thenReturn(topologyRequestStep1);
        when(topologyRequestStep1.send()).thenReturn(zeebeFuture);
        when(zeebeFuture.get(anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new RuntimeException("Connection failed"));

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
        assertThat(health.getDetails().get("error").toString())
                .contains("RuntimeException")
                .contains("Connection failed");

        verify(zeebeClient).newTopologyRequest();
    }

    @Test
    void health_shouldReturnCachedResult_whenCalledWithinCacheDuration() throws Exception {
        // Given
        when(zeebeClient.newTopologyRequest()).thenReturn(topologyRequestStep1);
        when(topologyRequestStep1.send()).thenReturn(zeebeFuture);
        when(zeebeFuture.get(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(topology);
        when(topology.getBrokers()).thenReturn(Collections.singletonList(brokerInfo));
        when(topology.getClusterSize()).thenReturn(3);
        when(topology.getPartitionsCount()).thenReturn(6);

        // When - First call
        Health firstHealth = healthIndicator.health();

        // When - Second call immediately after (within cache duration)
        Health secondHealth = healthIndicator.health();

        // Then
        assertThat(firstHealth.getStatus()).isEqualTo(Status.UP);
        assertThat(secondHealth.getStatus()).isEqualTo(Status.UP);
        assertThat(firstHealth).isSameAs(secondHealth); // Same cached instance

        // Verify Zeebe was only called once
        verify(zeebeClient, times(1)).newTopologyRequest();
        verify(zeebeFuture, times(1)).get(anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void health_shouldMakeNewRequest_whenCacheExpires() throws Exception {
        // Given
        ReflectionTestUtils.setField(healthIndicator, "cacheDurationMs", 100L); // 100ms cache

        when(zeebeClient.newTopologyRequest()).thenReturn(topologyRequestStep1);
        when(topologyRequestStep1.send()).thenReturn(zeebeFuture);
        when(zeebeFuture.get(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(topology);
        when(topology.getBrokers()).thenReturn(Collections.singletonList(brokerInfo));
        when(topology.getClusterSize()).thenReturn(3);
        when(topology.getPartitionsCount()).thenReturn(6);

        // When - First call
        Health firstHealth = healthIndicator.health();

        // Wait for cache to expire
        Thread.sleep(150);

        // When - Second call after cache expires
        Health secondHealth = healthIndicator.health();

        // Then
        assertThat(firstHealth.getStatus()).isEqualTo(Status.UP);
        assertThat(secondHealth.getStatus()).isEqualTo(Status.UP);

        // Verify Zeebe was called twice
        verify(zeebeClient, times(2)).newTopologyRequest();
        verify(zeebeFuture, times(2)).get(anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void health_shouldReturnUp_withMultipleBrokers() throws Exception {
        // Given
        when(zeebeClient.newTopologyRequest()).thenReturn(topologyRequestStep1);
        when(topologyRequestStep1.send()).thenReturn(zeebeFuture);
        when(zeebeFuture.get(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(topology);
        when(topology.getBrokers()).thenReturn(Collections.nCopies(3, brokerInfo));
        when(topology.getClusterSize()).thenReturn(3);
        when(topology.getPartitionsCount()).thenReturn(9);

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("brokers", 3);
        assertThat(health.getDetails()).containsEntry("clusterSize", 3);
        assertThat(health.getDetails()).containsEntry("partitions", 9);
    }

    @Test
    void health_shouldCacheDownStatus_whenZeebeIsDown() throws Exception {
        // Given
        when(zeebeClient.newTopologyRequest()).thenReturn(topologyRequestStep1);
        when(topologyRequestStep1.send()).thenReturn(zeebeFuture);
        when(zeebeFuture.get(anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new RuntimeException("Connection failed"));

        // When - First call
        Health firstHealth = healthIndicator.health();

        // When - Second call immediately after (within cache duration)
        Health secondHealth = healthIndicator.health();

        // Then
        assertThat(firstHealth.getStatus()).isEqualTo(Status.DOWN);
        assertThat(secondHealth.getStatus()).isEqualTo(Status.DOWN);
        assertThat(firstHealth).isSameAs(secondHealth); // Same cached instance

        // Verify Zeebe was only called once (down status is also cached)
        verify(zeebeClient, times(1)).newTopologyRequest();
        verify(zeebeFuture, times(1)).get(anyLong(), eq(TimeUnit.SECONDS));
    }
}
