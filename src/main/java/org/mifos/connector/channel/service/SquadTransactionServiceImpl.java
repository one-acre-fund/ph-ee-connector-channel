package org.mifos.connector.channel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultExchange;
import org.mifos.connector.channel.model.SquadTransactionResponse;
import org.mifos.connector.channel.model.SquadTransactionResponseList;
import org.mifos.connector.channel.model.SquadTransactionsSyncRequest;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.mifos.connector.channel.zeebe.ZeebeVariables.CORRELATION_ID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SquadTransactionServiceImpl implements SquadTransactionService {

    private final ProducerTemplate producerTemplate;
    private final ExecutorService executorService;
    private final ObjectMapper objectMapper;

    @Override
    public SquadTransactionResponseList syncTransactions(SquadTransactionsSyncRequest transactionsSyncRequest) {
        List<Future<SquadTransactionResponse>> futures = new ArrayList<>();
        final var correlationId = transactionsSyncRequest.getCorrelationId();
        for (final var districtToken : transactionsSyncRequest.getDistrictTokens()) {
            futures.add(executorService.submit(() -> syncTransactionsByToken(objectMapper.writeValueAsString(Map.of(CORRELATION_ID, correlationId)), districtToken)));
        }
        // Collect results
        List<SquadTransactionResponse> squadTransactionResponses = new ArrayList<>();
        for (final var future : futures) {
            try {
                squadTransactionResponses.add(future.get());  // Blocking call (waits for task to finish)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupt status
                log.error(e.getMessage(), e);
            } catch (ExecutionException e) {
                log.error(e.getMessage(), e);
            }
        }
        return SquadTransactionResponseList.builder()
                .squadTransactionResponses(squadTransactionResponses)
                .build();
    }

    /** * Sync transactions for a specific district token.
     *
     * @param correlationId Correlation ID for the sync request.
     * @param districtToken District token to identify the district.
     * @return SquadTransactionResponse containing the sync result.
     */
    private SquadTransactionResponse syncTransactionsByToken(String correlationId, String districtToken) {
        DefaultExchange exchange = new DefaultExchange(producerTemplate.getCamelContext());
        exchange.getIn().setBody(correlationId);
        exchange.getIn().setHeader("District-Token", districtToken);
        producerTemplate.send("direct:squad-transactions-sync", exchange);
        return exchange.getIn().getBody(SquadTransactionResponse.class);
    }

    @PreDestroy
    public void shutdownExecutor() {
        log.info("Shutting down executor service");
        executorService.shutdown();
    }
}
