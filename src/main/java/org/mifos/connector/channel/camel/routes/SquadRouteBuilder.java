package org.mifos.connector.channel.camel.routes;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bean.validator.BeanValidationException;
import org.mifos.connector.channel.exception.InvalidSignatureException;
import org.mifos.connector.channel.model.LiteChannelRequest;
import org.mifos.connector.channel.model.SquadProps;
import org.mifos.connector.channel.model.SquadTransactionRequest;
import org.mifos.connector.channel.model.SquadTransactionResponse;
import org.mifos.connector.channel.zeebe.ZeebeProcessStarter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.mifos.connector.channel.camel.config.CamelProperties.SQUAD_PROVIDER_NAME;
import static org.mifos.connector.channel.camel.config.CamelProperties.SQUAD_SIGNATURE_HEADER;
import static org.mifos.connector.channel.utils.PosPaymentUtils.isValidSignature;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.AMOUNT;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.AMS;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.CURRENCY;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.EXTERNAL_ID;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.IS_NOTIFICATIONS_FAILURE_ENABLED;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.IS_NOTIFICATIONS_SUCCESS_ENABLED;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.PAYMENT_SCHEME;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.TRANSACTION_ID;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.VIRTUAL_ACCOUNT_NUMBER;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.WEBHOOK_PAYLOAD;

/**
 * Camel route builder for handling Squad payment transactions.
 */
@Component
@Slf4j
public class SquadRouteBuilder extends RouteBuilder {
    private final ZeebeProcessStarter zeebeProcessStarter;
    private final ObjectMapper objectMapper;
    private final SquadProps squadProps;

    public SquadRouteBuilder(ZeebeProcessStarter zeebeProcessStarter, SquadProps squadProps, ObjectMapper objectMapper) {
        this.zeebeProcessStarter = zeebeProcessStarter;
        this.objectMapper = objectMapper;
        this.squadProps = squadProps;
    }

    @Override
    public void configure() throws Exception {

        onException(BeanValidationException.class).handled(true).process(exchange -> {
            Map<String, String> errors = new HashMap<>();
            BeanValidationException exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT,
                BeanValidationException.class);
            exception.getConstraintViolations().forEach(violation -> {
                errors.put(violation.getPropertyPath().toString(), violation.getMessage());
            });
            String transactionRef = exchange.getProperty(TRANSACTION_ID, String.class);
            exchange.getIn().setBody(SquadTransactionResponse.builder().responseCode(400).transactionReference(transactionRef)
                .responseDescription("Validation failure").errors(errors).build());
            exchange.getIn().setHeader(HTTP_RESPONSE_CODE, 400);
        });

        onException(InvalidSignatureException.class).handled(true).process(exchange -> {
            Map<String, String> errors = new HashMap<>();
            errors.put("signature", "Invalid payload signature");
            String transactionRef = exchange.getProperty(TRANSACTION_ID, String.class);
            exchange.getIn().setBody(SquadTransactionResponse.builder().responseCode(400).transactionReference(transactionRef)
                .responseDescription("Validation failure").errors(errors).build());
            exchange.getIn().setHeader(HTTP_RESPONSE_CODE, 400);
        });

        onException(Exception.class).handled(true).log(LoggingLevel.ERROR, "Caught exception: ${exception.stacktrace}")
            .process(exchange -> {
                String transactionRef = exchange.getProperty(TRANSACTION_ID, String.class);
                exchange.getIn().setBody(SquadTransactionResponse.builder().responseCode(500).transactionReference(transactionRef)
                    .responseDescription("System malfunction").build());
                exchange.getIn().setHeader(HTTP_RESPONSE_CODE, 500);
            });

        from("direct:squad-transaction-base")
            .id("squad-transaction-base")
            .log("Received squad transaction request: ${body}")
            .setProperty(WEBHOOK_PAYLOAD, simple("${body}"))
            .unmarshal().json(SquadTransactionRequest.class)
            .to("bean-validator:squad-txn-validator")
            .setProperty(TRANSACTION_ID, simple("${body.transactionReference}"))
            .to("direct:squad-signature-validation")
            .process(exchange -> {
                SquadTransactionRequest request = exchange.getIn().getBody(SquadTransactionRequest.class);
                Map<String, Object> variables = new HashMap<>();
                variables.put(PAYMENT_SCHEME, SQUAD_PROVIDER_NAME);
                variables.put(WEBHOOK_PAYLOAD, exchange.getProperty(WEBHOOK_PAYLOAD));
                variables.put(EXTERNAL_ID, request.getTransactionReference());
                variables.put(AMS, squadProps.getAmsValue());
                variables.put(CURRENCY, request.getCurrency());
                variables.put(AMOUNT, request.getPrincipalAmount());
                variables.put(VIRTUAL_ACCOUNT_NUMBER, request.getVirtualAccountNumber());
                variables.put(IS_NOTIFICATIONS_SUCCESS_ENABLED, squadProps.isSuccessNotificationsEnabled());
                variables.put(IS_NOTIFICATIONS_FAILURE_ENABLED, squadProps.isFailureNotificationsEnabled());
                LiteChannelRequest channelRequest = LiteChannelRequest.fromSquadTransactionRequest(request, squadProps);
                String transactionId = zeebeProcessStarter.startZeebeWorkflow(squadProps.getFlow(), objectMapper.writeValueAsString(channelRequest), variables);
                log.info("Started workflow for squad payment with transaction reference: {} and id: {}", request.getTransactionReference(), transactionId);
                exchange.getIn().setHeader(HTTP_RESPONSE_CODE, 200);
                exchange.getIn().setBody(SquadTransactionResponse.builder().responseCode(200).transactionReference(request.getTransactionReference())
                    .responseDescription("Success").build());
            });

        from("direct:squad-signature-validation")
            .id("squad-signature-validation")
            .process(exchange -> {
                String signature = exchange.getIn().getHeader(SQUAD_SIGNATURE_HEADER, String.class);
                String body = exchange.getProperty(WEBHOOK_PAYLOAD, String.class);
                if (!isValidSignature(signature, squadProps.getSecretKey(), body)) {
                    log.error("Squad signature validation failed for request: {}. Received signature: {}", body, signature);
                    throw new InvalidSignatureException();
                }
            });
    }
}
