package org.mifos.connector.channel.utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Rejects collection requests that can never be processed, before a workflow is started for them.
 * Payment-scheme specific rules (e.g. M-Pesa only accepting whole amounts) stay in the scheme connectors.
 */
public final class CollectionRequestValidator {

    private static final String MSISDN_KEY = "MSISDN";
    // Digits only, optional leading '+', no country-specific format. E.164 numbers are at most 15 digits.
    private static final Pattern MSISDN_PATTERN = Pattern.compile("\\+?\\d{1,15}");

    private CollectionRequestValidator() {
    }

    /**
     * @return a description of the first problem found, or empty if the request is valid
     */
    public static Optional<String> validate(JSONObject body) {
        JSONArray payer = body.optJSONArray("payer");
        if (payer == null || payer.length() < 2) {
            return Optional.of("Payer must contain an MSISDN and an account identifier");
        }

        String msisdn = null;
        boolean hasMsisdn = false;
        for (int i = 0; i < payer.length(); i++) {
            JSONObject party = payer.optJSONObject(i);
            if (party == null || !(party.opt("key") instanceof String) || !(party.opt("value") instanceof String)) {
                return Optional.of("Every payer entry must have a string key and value");
            }
            if (MSISDN_KEY.equalsIgnoreCase(party.getString("key"))) {
                hasMsisdn = true;
                msisdn = party.getString("value").trim();
            }
        }
        if (!hasMsisdn) {
            return Optional.of("Payer MSISDN is missing");
        }
        if (!MSISDN_PATTERN.matcher(msisdn).matches()) {
            return Optional.of("Payer MSISDN '" + msisdn + "' is invalid, it must contain digits only");
        }

        JSONObject amount = body.optJSONObject("amount");
        Object rawAmount = amount == null ? null : amount.opt("amount");
        if (!(rawAmount instanceof String) || ((String) rawAmount).isBlank()) {
            return Optional.of("Amount is missing");
        }
        try {
            if (new BigDecimal(((String) rawAmount).trim()).signum() <= 0) {
                return Optional.of("Amount '" + rawAmount + "' must be greater than zero");
            }
        } catch (NumberFormatException e) {
            return Optional.of("Amount '" + rawAmount + "' is not a number");
        }
        return Optional.empty();
    }
}
