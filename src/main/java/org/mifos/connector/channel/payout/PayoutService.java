package org.mifos.connector.channel.payout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mifos.connector.channel.model.Payer;
import org.mifos.connector.channel.model.PayoutRequestDTO;
import org.mifos.connector.channel.utils.AMSProps;
import org.mifos.connector.channel.utils.AMSUtils;
import org.mifos.connector.channel.zeebe.ZeebeProcessStarter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mifos.connector.channel.zeebe.ZeebeVariables.AMS;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.CLIENT_CORRELATION_ID;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.PAYMENT_SCHEME;
import static org.mifos.connector.channel.zeebe.ZeebeVariables.TENANT_ID;

/**
 * Starts payouts to customer wallets on any mobile money scheme. The channel only checks what is true for every scheme
 * and starts {@code {ps}_payout_{ams}-{dfspid}}; the scheme connector sends the payment (see the README contract).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {

    private static final String DEFAULT_AMS_IDENTIFIER = "default";

    private final PayoutProperties properties;
    private final PaymentSchemeResolver paymentSchemeResolver;
    private final ZeebeProcessStarter zeebeProcessStarter;
    private final AMSUtils amsUtils;
    private final ObjectMapper objectMapper;

    @Value("#{'${dfspids}'.split(',')}")
    private final List<String> dfspIds;

    @Value("${mpesa.notification.success.enabled}")
    private final boolean notificationsSuccessEnabled;

    @Value("${mpesa.notification.failure.enabled}")
    private final boolean notificationsFailureEnabled;

    /**
     * @return the transaction id; the same tenant and correlation id always give the same one
     * @throws PayoutRejectedException when the payout was not started
     */
    public String startPayout(String tenantId, String clientCorrelationId, String paymentSchemeHeader,
                              PayoutRequestDTO request) {
        if (!properties.isEnabled()) {
            throw new PayoutRejectedException(HttpStatus.FORBIDDEN, "Payouts are not enabled", null);
        }
        if (tenantId == null || !dfspIds.contains(tenantId)) {
            throw PayoutRejectedException.invalid("Requested tenant " + tenantId + " not configured in the connector");
        }
        if (StringUtils.isBlank(clientCorrelationId)) {
            throw PayoutRejectedException.invalid("X-CorrelationID is required");
        }
        PayoutRequestValidator.validate(request).ifPresent(error -> {
            log.warn("Rejecting payout request for tenant {} with correlation id {}: {}", tenantId,
                    clientCorrelationId, error);
            throw PayoutRejectedException.invalid(error);
        });

        var msisdn = PayoutRequestValidator.msisdn(request.getPayee()).orElseThrow();
        var account = PayoutRequestValidator.account(request.getPayee());
        var ams = account.map(Payer::getKey).map(this::amsFor).orElseGet(this::defaultAms);
        if (ams == null) {
            throw PayoutRejectedException.invalid("No account system is configured for payee account "
                    + account.map(Payer::getKey).orElse("(none)"));
        }
        var paymentScheme = paymentSchemeResolver.resolve(paymentSchemeHeader, msisdn);
        var workflow = properties.workflowFor(paymentScheme, ams, tenantId);
        var transactionId = PayoutRequestValidator.transactionId(tenantId, clientCorrelationId);

        var variables = new HashMap<String, Object>();
        variables.put(TENANT_ID, tenantId);
        variables.put(AMS, ams);
        variables.put(PAYMENT_SCHEME, paymentScheme);
        variables.put("scenario", paymentScheme);
        variables.put("initiator", "PAYER");
        variables.put("initiatorType", "BUSINESS");
        variables.put(CLIENT_CORRELATION_ID, clientCorrelationId);
        variables.put("phoneNumber", msisdn);
        variables.put("accountId", account.map(Payer::getValue).map(String::trim).orElse(null));
        variables.put("amount", request.getAmount().getAmount().trim());
        variables.put("currency", request.getAmount().getCurrency().trim());
        variables.put("remarks", StringUtils.trimToEmpty(request.getRemarks()));
        variables.put("customData", request.getCustomData() == null ? "" : toJson(request.getCustomData()));
        variables.put("payoutRequest", toJson(request));
        variables.put("payoutTimer", properties.getTimer());
        variables.put("isNotificationsSuccessEnabled", notificationsSuccessEnabled);
        variables.put("isNotificationsFailureEnabled", notificationsFailureEnabled);
        addCallbackUrl(variables);

        start(workflow, transactionId, variables, ams, paymentScheme, tenantId);
        return transactionId;
    }

    private void addCallbackUrl(Map<String, Object> variables) {
        try {
            amsUtils.addCallbackUrlToVariables((String) variables.get("customData"), variables);
        } catch (IllegalArgumentException e) {
            throw PayoutRejectedException.invalid(e.getMessage());
        }
    }

    private void start(String workflow, String transactionId, Map<String, Object> variables, String ams,
                       String paymentScheme, String tenantId) {
        try {
            zeebeProcessStarter.startZeebeWorkflowWithTransactionId(workflow, transactionId, variables);
        } catch (ClientStatusException e) {
            log.error("Payout workflow {} failed to start for transaction {}: {}", workflow, transactionId,
                    e.getMessage());
            if (e.getStatusCode() == Status.Code.NOT_FOUND) {
                throw new PayoutRejectedException(HttpStatus.NOT_FOUND, String.format("Zeebe workflow not found for "
                        + "ams %s, payment scheme %s and tenant %s", ams, paymentScheme, tenantId), e.getMessage());
            }
            throw new PayoutRejectedException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Failed to start Zeebe workflow for the moment", e.getMessage());
        }
    }

    /**
     * The AMS holding accounts with this identifier key, as for collections, falling back to the default AMS.
     */
    private String amsFor(String accountKey) {
        return amsGroups().stream()
                .filter(group -> group.getIdentifier().equalsIgnoreCase(accountKey))
                .map(AMSProps.AMS::getValue)
                .findFirst()
                .orElseGet(this::defaultAms);
    }

    private String defaultAms() {
        return amsGroups().stream()
                .filter(group -> DEFAULT_AMS_IDENTIFIER.equalsIgnoreCase(group.getIdentifier()))
                .map(AMSProps.AMS::getValue)
                .findFirst()
                .orElse(null);
    }

    private List<AMSProps.AMS> amsGroups() {
        return amsUtils.postConstruct();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
