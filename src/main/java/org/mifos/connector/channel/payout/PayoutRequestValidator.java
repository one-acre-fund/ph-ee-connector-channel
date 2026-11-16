package org.mifos.connector.channel.payout;

import org.apache.commons.lang3.StringUtils;
import org.mifos.connector.channel.model.Amount;
import org.mifos.connector.channel.model.Payer;
import org.mifos.connector.channel.model.PayoutRequestDTO;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Rejects payout requests that can never be processed on any payment scheme, before a workflow is started. Scheme rules
 * (e.g. M-Pesa only paying whole KES amounts to 254 numbers, remark lengths, MTN's currency per market) stay in the
 * scheme connectors, so the same endpoint serves every mobile money provider.
 */
public final class PayoutRequestValidator {

    static final String MSISDN_KEY = "MSISDN";
    // Digits only, optional leading '+'. E.164 numbers are at most 15 digits.
    private static final Pattern MSISDN_PATTERN = Pattern.compile("\\+?\\d{1,15}");
    // Optional; each connector trims or limits it further to what its provider accepts
    static final int MAX_REMARKS = 255;

    private PayoutRequestValidator() {
    }

    /**
     * @return a description of the first problem found, or empty if the request is valid
     */
    public static Optional<String> validate(PayoutRequestDTO request) {
        List<Payer> payee = request.getPayee();
        if (payee == null || payee.isEmpty()) {
            return Optional.of("Payee is missing");
        }
        if (payee.stream().anyMatch(party -> party == null || party.getKey() == null || party.getValue() == null)) {
            return Optional.of("Every payee entry must have a key and a value");
        }
        var msisdn = msisdn(payee);
        if (msisdn.isEmpty()) {
            return Optional.of("Payee MSISDN is missing");
        }
        if (!MSISDN_PATTERN.matcher(msisdn.get()).matches()) {
            return Optional.of("Payee MSISDN '" + msisdn.get() + "' is invalid, it must contain digits only");
        }
        return validateAmount(request.getAmount()).or(() -> validateRemarks(request.getRemarks()));
    }

    private static Optional<String> validateAmount(Amount amount) {
        var value = amount == null ? null : amount.getAmount();
        if (StringUtils.isBlank(value)) {
            return Optional.of("Amount is missing");
        }
        try {
            if (new BigDecimal(value.trim()).signum() <= 0) {
                return Optional.of("Amount '" + value + "' must be greater than zero");
            }
        } catch (NumberFormatException e) {
            return Optional.of("Amount '" + value + "' is not a number");
        }
        if (StringUtils.isBlank(amount.getCurrency())) {
            return Optional.of("Currency is missing");
        }
        return Optional.empty();
    }

    private static Optional<String> validateRemarks(String remarks) {
        if (remarks != null && remarks.trim().length() > MAX_REMARKS) {
            return Optional.of("Remarks must be at most " + MAX_REMARKS + " characters");
        }
        return Optional.empty();
    }

    /**
     * The payee's MSISDN, trimmed.
     */
    public static Optional<String> msisdn(List<Payer> payee) {
        return firstValue(payee, PayoutRequestValidator::isMsisdn);
    }

    /**
     * The payee's account identifier in the AMS: its first non-MSISDN entry, e.g. FINERACTACCOUNTID.
     */
    public static Optional<Payer> account(List<Payer> payee) {
        return payee.stream().filter(Objects::nonNull).filter(Predicate.not(PayoutRequestValidator::isMsisdn))
                .findFirst();
    }

    /**
     * The payout's transaction id, derived from the tenant and the client correlation id. A resent request gets the
     * same id, which the provider uses to refuse a second payment (M-Pesa B2C OriginatorConversationID, MTN MoMo
     * X-Reference-Id; it is a UUID, as MTN requires).
     */
    public static String transactionId(String tenantId, String clientCorrelationId) {
        return UUID.nameUUIDFromBytes(("payout:" + tenantId + ":" + clientCorrelationId)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static boolean isMsisdn(Payer party) {
        return MSISDN_KEY.equalsIgnoreCase(party.getKey());
    }

    private static Optional<String> firstValue(List<Payer> payee, Predicate<Payer> filter) {
        return payee.stream().filter(Objects::nonNull).filter(filter).map(Payer::getValue).filter(Objects::nonNull)
                .map(String::trim).findFirst();
    }
}
