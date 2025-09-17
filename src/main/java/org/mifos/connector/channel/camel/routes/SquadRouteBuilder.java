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
import org.mifos.connector.channel.model.SquadTransactionsSyncRequest;
import org.mifos.connector.channel.model.SquadWebhookLogResponse;
import org.mifos.connector.channel.zeebe.ZeebeProcessStarter;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.mifos.connector.channel.camel.config.CamelProperties.AUTHORIZATION;
import static org.mifos.connector.channel.camel.config.CamelProperties.HAS_MORE;
import static org.mifos.connector.channel.camel.config.CamelProperties.PAGE;
import static org.mifos.connector.channel.camel.config.CamelProperties.SQUAD_PROVIDER_NAME;
import static org.mifos.connector.channel.camel.config.CamelProperties.SQUAD_SIGNATURE_HEADER;
import static org.mifos.connector.channel.utils.PosPaymentUtils.isValidSignature;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.AMOUNT;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.AMS;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.CORRELATION_ID;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.CURRENCY;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.DISTRICT_TOKEN;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.EXTERNAL_ID;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.IS_MISSED_WEBHOOK_NOTIFICATION;
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
            .to("direct:start-squad-txn-workflow")
            .setBody(exchange -> {
                String transactionRef = exchange.getProperty(TRANSACTION_ID, String.class);
                return SquadTransactionResponse.builder().responseCode(200).transactionReference(transactionRef)
                    .responseDescription("Success").build();
            })
            .setHeader(HTTP_RESPONSE_CODE, constant(200));

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

        from("direct:squad-transactions-sync")
            .id("squad-transactions-sync")
            .log("Received squad transactions sync request: ${body}")
            .unmarshal().json(SquadTransactionsSyncRequest.class)
            .to("bean-validator:squad-txn-sync-validator")
            .setProperty(CORRELATION_ID, simple("${body.correlationId}"))
            .wireTap("direct:fetch-missed-webhook-logs")
            .setBody(exchange -> SquadTransactionResponse.builder().responseCode(202)
                .transactionReference(exchange.getProperty(CORRELATION_ID, String.class))
                .responseDescription("Squad transactions sync initiated").build())
            .setHeader(HTTP_RESPONSE_CODE, constant(202));


        from("direct:fetch-missed-webhook-logs")
            .id("fetch-missed-webhook-logs")
            .setProperty(PAGE, constant(1))
            .setProperty(HAS_MORE, constant(true))
            .loopDoWhile(simple("${exchangeProperty[hasMore]} == true"))
                .setProperty("districtToken", header(DISTRICT_TOKEN))
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setHeader(AUTHORIZATION, simple("Bearer ${exchangeProperty[districtToken]}"))
                .toD(squadProps.getBaseUrl() + squadProps.getLogsEndpoint()
                    + "?page=${exchangeProperty[page]}&perPage=" + squadProps.getLogsPageSize()
                + "&bridgeEndpoint=true&throwExceptionOnFailure=false")
                .choice()
                    .when(header(Exchange.HTTP_RESPONSE_CODE).isEqualTo(200))
                        .to("direct:handle-webhook-logs-success")
                    .otherwise()
                        .process(exchange -> {
                            String error = exchange.getIn().getBody(String.class);
                            log.error("Failed to fetch squad webhook logs. Correlation ID: {}. Response: {}",
                                exchange.getProperty(CORRELATION_ID), error);
                            exchange.setProperty(HAS_MORE, false);
                        });

        from("direct:start-squad-txn-workflow")
            .id("start-squad-txn-workflow")
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
                variables.put(IS_MISSED_WEBHOOK_NOTIFICATION, Boolean.TRUE.equals(exchange.getProperty(IS_MISSED_WEBHOOK_NOTIFICATION, Boolean.class)));
                variables.put(CORRELATION_ID, exchange.getProperty(CORRELATION_ID));
                LiteChannelRequest channelRequest = LiteChannelRequest.fromSquadTransactionRequest(request, squadProps);
                String transactionId = zeebeProcessStarter.startZeebeWorkflow(squadProps.getFlow(), objectMapper.writeValueAsString(channelRequest), variables);
                log.info("Started workflow for squad payment with transaction reference: {} and id: {}", request.getTransactionReference(), transactionId);
            });

        from("direct:handle-webhook-logs-success")
            .id("handle-webhook-logs-success")
            .unmarshal().json(SquadWebhookLogResponse.class)
            .process(exchange -> {
                SquadWebhookLogResponse response = exchange.getIn().getBody(SquadWebhookLogResponse.class);
                SquadWebhookLogResponse.Data data = response.getData();
                if (data != null && !CollectionUtils.isEmpty(data.getRows())) {
                    List<SquadWebhookLogResponse.Row> missedWebhookLogs = data.getRows();
                    exchange.getIn().setBody(missedWebhookLogs);
                    // If size of rows is greater than or equal to page size, it indicates there might be more logs to fetch
                    exchange.setProperty(HAS_MORE, missedWebhookLogs.size() >= squadProps.getLogsPageSize());
                } else {
                    exchange.setProperty(HAS_MORE, false);
                    exchange.getIn().setBody(List.of());
                }
            })
            .split(body()).parallelProcessing()
                .to("direct:start-squad-missed-webhook-flow")
            .end()
            .process(exchange -> {
                Integer currentPage = exchange.getProperty(PAGE, Integer.class);
                exchange.setProperty(PAGE, currentPage + 1);
            });


        from("direct:start-squad-missed-webhook-flow")
            .id("start-squad-missed-webhook-flow")
            .process(exchange -> {
                SquadTransactionRequest request = exchange.getIn().getBody(SquadWebhookLogResponse.Row.class).getPayload();
                exchange.setProperty(TRANSACTION_ID, request.getTransactionReference());
                exchange.setProperty(WEBHOOK_PAYLOAD, objectMapper.writeValueAsString(request));
                exchange.setProperty(IS_MISSED_WEBHOOK_NOTIFICATION, true);
                exchange.getIn().setBody(request);
            })
            .to("direct:start-squad-txn-workflow");

        from("direct:delete-webhook-log")
            .id("delete-webhook-log")
            .removeHeader("*")
            .setHeader(Exchange.HTTP_METHOD, constant("DELETE"))
            .setHeader(AUTHORIZATION, simple("Bearer " + squadProps.getToken()))
            .toD(squadProps.getBaseUrl() + squadProps.getLogsEndpoint()
                + "/${exchangeProperty." + TRANSACTION_ID + "}"
                + "?bridgeEndpoint=true&throwExceptionOnFailure=false")
            .log(LoggingLevel.INFO, "Squad Webhook log deletion API called for transaction:"
                + " ${exchangeProperty." + TRANSACTION_ID + "}, response: \n\n ${body}");


    }
}
