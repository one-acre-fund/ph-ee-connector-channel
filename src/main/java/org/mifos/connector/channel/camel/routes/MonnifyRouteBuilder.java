package org.mifos.connector.channel.camel.routes;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bean.validator.BeanValidationException;
import org.mifos.connector.channel.exception.InvalidSignatureException;
import org.mifos.connector.channel.model.LiteChannelRequest;
import org.mifos.connector.channel.model.MonnifyProps;
import org.mifos.connector.channel.model.MonnifyTransactionRequest;
import org.mifos.connector.channel.model.MonnifyTransactionResponse;
import org.mifos.connector.channel.zeebe.ZeebeProcessStarter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.mifos.connector.channel.camel.config.CamelProperties.MONNIFY_SIGNATURE_HEADER;
import static org.mifos.connector.channel.camel.config.CamelProperties.SQUAD_PROVIDER_NAME;
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
 * Camel route builder for handling Monnify payment transactions.
 */
@Component
@Slf4j
public class MonnifyRouteBuilder extends RouteBuilder {
    private final ZeebeProcessStarter zeebeProcessStarter;
    private final ObjectMapper objectMapper;
    private final MonnifyProps monnifyProps;

    public MonnifyRouteBuilder(ZeebeProcessStarter zeebeProcessStarter, MonnifyProps monnifyProps, ObjectMapper objectMapper) {
        this.zeebeProcessStarter = zeebeProcessStarter;
        this.objectMapper = objectMapper;
        this.monnifyProps = monnifyProps;
    }

    @Override
    public void configure() throws Exception {

        onException(BeanValidationException.class).handled(true).process(exchange -> {
            BeanValidationException exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT,
                BeanValidationException.class);
            Map<String, String> errors = new HashMap<>();
            exception.getConstraintViolations().forEach(violation -> {
                errors.put(violation.getPropertyPath().toString(), violation.getMessage());
            });
            String transactionRef = exchange.getProperty(TRANSACTION_ID, String.class);
            exchange.getIn().setBody(MonnifyTransactionResponse.builder().transactionReference(transactionRef).errors(errors).build());
            exchange.getIn().setHeader(HTTP_RESPONSE_CODE, 400);
        });

        onException(InvalidSignatureException.class).handled(true).process(exchange -> {
            String transactionRef = exchange.getProperty(TRANSACTION_ID, String.class);
            exchange.getIn().setBody(MonnifyTransactionResponse.builder().transactionReference(transactionRef)
                .message("Invalid payload signature").build());
            exchange.getIn().setHeader(HTTP_RESPONSE_CODE, 400);
        });

        onException(Exception.class).handled(true).log(LoggingLevel.ERROR, "Caught exception: ${exception.stacktrace}")
            .process(exchange -> {
                String transactionRef = exchange.getProperty(TRANSACTION_ID, String.class);
                exchange.getIn().setBody(MonnifyTransactionResponse.builder().transactionReference(transactionRef)
                    .message("Error occurred while handling the request").build());
                exchange.getIn().setHeader(HTTP_RESPONSE_CODE, 500);
            });

        from("direct:monnify-transaction-base")
            .id("monnify-transaction-base")
            .log("Received monnify transaction request: ${body}")
            .setProperty(WEBHOOK_PAYLOAD, simple("${body}"))
            .unmarshal().json(MonnifyTransactionRequest.class)
            .to("bean-validator:monnify-txn-validator")
            .setProperty(TRANSACTION_ID, simple("${body.eventData.transactionReference}"))
            .to("direct:monnify-signature-validation")
            .process(exchange -> {
                MonnifyTransactionRequest request = exchange.getIn().getBody(MonnifyTransactionRequest.class);
                String transactionReference = request.getEventData().getTransactionReference();
                Map<String, Object> variables = new HashMap<>();
                variables.put(PAYMENT_SCHEME, SQUAD_PROVIDER_NAME);
                variables.put(WEBHOOK_PAYLOAD, exchange.getProperty(WEBHOOK_PAYLOAD));
                variables.put(EXTERNAL_ID, transactionReference);
                variables.put(AMS, monnifyProps.getAmsValue());
                variables.put(CURRENCY, request.getEventData().getCurrency());
                variables.put(AMOUNT, request.getEventData().getAmountPaid());
                variables.put(VIRTUAL_ACCOUNT_NUMBER, request.getEventData().getPaymentReference());
                variables.put(IS_NOTIFICATIONS_SUCCESS_ENABLED, monnifyProps.isSuccessNotificationsEnabled());
                variables.put(IS_NOTIFICATIONS_FAILURE_ENABLED, monnifyProps.isFailureNotificationsEnabled());
                LiteChannelRequest channelRequest = LiteChannelRequest.fromMonnifyTransactionRequest(request.getEventData(), monnifyProps);
                String transactionId = zeebeProcessStarter.startZeebeWorkflow(monnifyProps.getFlow(), objectMapper.writeValueAsString(channelRequest), variables);
                log.info("Started workflow for monnify payment with transaction reference: {} and id: {}", transactionReference, transactionId);
                exchange.getIn().setHeader(HTTP_RESPONSE_CODE, 200);
                exchange.getIn().setBody(MonnifyTransactionResponse.builder().transactionReference(transactionReference)
                    .message("Success").build());
            });

        from("direct:monnify-signature-validation")
            .id("monnify-signature-validation")
            .process(exchange -> {
                String signature = exchange.getIn().getHeader(MONNIFY_SIGNATURE_HEADER, String.class);
                String body = exchange.getProperty(WEBHOOK_PAYLOAD, String.class);
                if (!isValidSignature(signature, monnifyProps.getSecretKey(), body)) {
                    log.error("Monnify signature validation failed for request: {}. Received signature: {}", body, signature);
                    throw new InvalidSignatureException();
                }
            });
    }
}
