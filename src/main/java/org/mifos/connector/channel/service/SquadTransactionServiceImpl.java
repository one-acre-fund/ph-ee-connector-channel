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
import java.util.stream.Collectors;

import static org.mifos.connector.channel.zeebe.ZeebeVariables.CORRELATION_ID;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.DISTRICT_TOKEN;

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
        final String correlationPayloadJson;
        try {
                correlationPayloadJson = objectMapper.writeValueAsString(Map.of(CORRELATION_ID, correlationId));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
               throw new IllegalStateException("Failed to serialize correlation payload", e);
            }

        for (final var districtToken : transactionsSyncRequest.getDistrictTokens()) {
            futures.add(executorService.submit(() -> syncTransactionsByToken(correlationPayloadJson, districtToken)));

        }


        // Collect results
        List<SquadTransactionResponse> squadTransactionResponses = new ArrayList<>();
        for (final var future : futures) {
            try {
                squadTransactionResponses.add(future.get());  // Blocking call (waits for task to finish)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupt status
                log.error(e.getMessage(), e);
                break;
            } catch (ExecutionException e) {
                log.error(e.getMessage(), e);
            }
        }
        return SquadTransactionResponseList.builder()
                .responseCode(getGeneralResponseStatus(squadTransactionResponses))
                .squadTransactionResponses(squadTransactionResponses)
                .build();
    }

    /** * Sync transactions for a specific district token.
     *
     * @param correlationPayloadJson Correlation ID for the sync request.
     * @param districtToken District token to identify the district.
     * @return SquadTransactionResponse containing the sync result.
     */
    private SquadTransactionResponse syncTransactionsByToken(String correlationPayloadJson, String districtToken) {
        DefaultExchange exchange = new DefaultExchange(producerTemplate.getCamelContext());
        exchange.getIn().setBody(correlationPayloadJson);
        exchange.getIn().setHeader(DISTRICT_TOKEN, districtToken);
        producerTemplate.send("direct:squad-transactions-sync", exchange);
        return exchange.getIn().getBody(SquadTransactionResponse.class);
    }

    /**
     * Determines the general response status based on individual transaction responses.
     *
     * @param squadTransactionResponses List of individual SquadTransactionResponse objects.
     * @return General HTTP status code representing the overall result.
     */
    private int getGeneralResponseStatus(List<SquadTransactionResponse> squadTransactionResponses) {
        final var responseStatuses = squadTransactionResponses.stream()
                .map(SquadTransactionResponse::getResponseCode)
                .distinct()
                .collect(Collectors.toList());
        int code;
        if (responseStatuses.isEmpty()) {
            code = 500; // Internal Server Error if no responses
        }else if (responseStatuses.size() > 1) {
            code = 207; // Multi-Status if mixed results
        } else if (responseStatuses.get(0) >= 200 && responseStatuses.get(0) < 300) {
            code = responseStatuses.get(0); // All successful
        } else if (responseStatuses.get(0) >= 400 && responseStatuses.get(0) < 500) {
            code = responseStatuses.get(0); // All client errors
        } else if (responseStatuses.get(0) >= 500) {
            code = responseStatuses.get(0); // All server errors
        } else {
            code = 500; // Default to Internal Server Error for unexpected codes
        }
        return code;
    }

    @PreDestroy
    public void shutdownExecutor() {
        log.info("Shutting down executor service");
        executorService.shutdown();
    }
}
