package org.mifos.connector.channel.api.definition;

import com.fasterxml.jackson.core.*;
import org.mifos.connector.channel.model.CollectionRequestDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.*;

import static org.mifos.connector.channel.camel.config.CamelProperties.PAYMENT_SCHEME_HEADER;

public interface CollectionApi {

    @PostMapping("/channel/collection")
    ResponseEntity<?> collection(@RequestHeader(value="Platform-TenantId") String tenant,
                                 @RequestHeader(value="X-CorrelationID") String correlationId,
                                 @RequestHeader(value = PAYMENT_SCHEME_HEADER, required = false) String paymentScheme,
                                 @RequestBody CollectionRequestDTO requestBody) throws ExecutionException, InterruptedException, JsonProcessingException;

}
