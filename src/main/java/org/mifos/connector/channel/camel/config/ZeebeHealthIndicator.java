package org.mifos.connector.channel.camel.config;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.Topology;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@RequiredArgsConstructor
@Component("zeebe")
public class ZeebeHealthIndicator implements HealthIndicator {

    private final ZeebeClient zeebeClient;
    private final AtomicReference<Health> cachedHealth = new AtomicReference<>();
    private final AtomicLong lastCheckTime = new AtomicLong(0);
    @Value("${zeebe-custom.health.cache-duration-ms:15000}")
    private long cacheDurationMs;
    @Value("${zeebe-custom.health.check-timeout-seconds:5}")
    private long healthCheckTimeoutSeconds;

    @Override
    public Health health() {
        long now = System.currentTimeMillis();
        long lastCheck = lastCheckTime.get();

        // Return cached result if still fresh
        Health cached = cachedHealth.get();
        if (cached != null && (now - lastCheck) < cacheDurationMs) {
            return cached;
        }

        // Perform actual check with timeout
        Health newHealth;
        try {
            Topology topology = zeebeClient.newTopologyRequest()
                    .send().get(healthCheckTimeoutSeconds, TimeUnit.SECONDS);

            newHealth = Health.up()
                    .withDetail("brokers", topology.getBrokers().size())
                    .withDetail("clusterSize", topology.getClusterSize())
                    .withDetail("partitions", topology.getPartitionsCount())
                    .build();

        } catch (TimeoutException e) {
            newHealth = Health.down()
                    .withDetail("error", "Zeebe health check timed out after " + healthCheckTimeoutSeconds + "s")
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            newHealth = Health.down()
                    .withDetail("error", "Zeebe health check interrupted")
                    .build();
        } catch (Exception e) {
            newHealth = Health.down()
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }

        cachedHealth.set(newHealth);
        lastCheckTime.set(now);

        return newHealth;
    }
}
