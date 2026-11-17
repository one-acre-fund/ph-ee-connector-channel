package org.mifos.connector.channel.payout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.api.command.ClientException;
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
import org.springframework.dao.DataAccessException;
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
    private final PayoutClaims claims;

    @Value("#{'${dfspids}'.split(',')}")
    private final List<String> dfspIds;

    @Value("${mpesa.notification.success.enabled}")
    private final boolean notificationsSuccessEnabled;

    @Value("${mpesa.notification.failure.enabled}")
    private final boolean notificationsFailureEnabled;

    /**
     * @return the transaction id; the same tenant and correlation id always give the same one
     * @throws PayoutRejectedException when no new payout was started; its outcome says whether one may be running
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

        claim(transactionId);
        start(workflow, transactionId, variables, ams, paymentScheme, tenantId);
        return transactionId;
    }

    /**
     * Claims the transaction id so a resent request can never start a second payout, on this pod or another.
     */
    private void claim(String transactionId) {
        try {
            if (claims.claim(transactionId)) {
                return;
            }
            var state = claims.state(transactionId).orElse(PayoutClaims.State.STARTING);
            log.warn("event=payout_duplicate transaction={} state={}", transactionId, state);
            throw new PayoutRejectedException(HttpStatus.CONFLICT, PayoutStatus.DUPLICATE, transactionId,
                    "Payout already submitted", state == PayoutClaims.State.UNCONFIRMED
                    ? "The first start was not confirmed; check this transaction before using a new X-CorrelationID"
                    : "A payout with this X-CorrelationID was already started; nothing new was started");
        } catch (DataAccessException e) {
            // Without the claim a duplicate cannot be ruled out, so nothing is started
            log.error("event=payout_claim_unavailable transaction={}: {}", transactionId, e.getMessage());
            throw new PayoutRejectedException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Payouts are unavailable: the duplicate check cannot be reached", e.getMessage());
        }
    }

    private void addCallbackUrl(Map<String, Object> variables) {
        try {
            amsUtils.addCallbackUrlToVariables((String) variables.get("customData"), variables);
        } catch (IllegalArgumentException e) {
            throw PayoutRejectedException.invalid(e.getMessage());
        }
    }

    /**
     * Starts the workflow and records the outcome on the claim:
     * <ul>
     *   <li>started: the claim is marked {@code STARTED};</li>
     *   <li>{@code NOT_FOUND} (no such workflow deployed): Zeebe created nothing, so the claim is released and the
     *   request can be sent again once the flow is deployed (404, {@code REJECTED});</li>
     *   <li>any other failure (unavailable, deadline exceeded, lost answer...): Zeebe may have created the instance,
     *   so the claim is kept as {@code UNCONFIRMED} and the caller gets the transaction id to check (503,
     *   {@code UNCONFIRMED}). A resend cannot start a second payout.</li>
     * </ul>
     */
    private void start(String workflow, String transactionId, Map<String, Object> variables, String ams,
                       String paymentScheme, String tenantId) {
        try {
            zeebeProcessStarter.startZeebeWorkflowWithTransactionId(workflow, transactionId, variables);
        } catch (ClientStatusException e) {
            if (e.getStatusCode() == Status.Code.NOT_FOUND) {
                log.error("Payout workflow {} is not deployed, transaction {} was not started", workflow,
                        transactionId);
                releaseClaim(transactionId);
                throw new PayoutRejectedException(HttpStatus.NOT_FOUND, String.format("Zeebe workflow not found for "
                        + "ams %s, payment scheme %s and tenant %s", ams, paymentScheme, tenantId), e.getMessage());
            }
            throw unconfirmed(workflow, transactionId, e);
        } catch (ClientException e) {
            throw unconfirmed(workflow, transactionId, e);
        }
        updateClaim(transactionId, PayoutClaims.State.STARTED);
    }

    private PayoutRejectedException unconfirmed(String workflow, String transactionId, RuntimeException cause) {
        log.error("event=payout_start_unconfirmed transaction={} workflow={}: {}", transactionId, workflow,
                cause.getMessage());
        updateClaim(transactionId, PayoutClaims.State.UNCONFIRMED);
        return new PayoutRejectedException(HttpStatus.SERVICE_UNAVAILABLE, PayoutStatus.UNCONFIRMED, transactionId,
                "Payout start not confirmed", "The payout may or may not have started. Check transaction "
                + transactionId + " before retrying; resending the same X-CorrelationID will not start a second "
                + "payout. Zeebe: " + cause.getMessage());
    }

    private void updateClaim(String transactionId, PayoutClaims.State state) {
        try {
            claims.update(transactionId, state);
        } catch (DataAccessException e) {
            // The claim itself still blocks a resend; only the state shown to the caller is less precise
            log.warn("Could not record payout {} as {}: {}", transactionId, state, e.getMessage());
        }
    }

    private void releaseClaim(String transactionId) {
        try {
            claims.release(transactionId);
        } catch (DataAccessException e) {
            log.warn("Could not release the claim of payout {}, it expires on its own: {}", transactionId,
                    e.getMessage());
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
