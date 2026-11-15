package org.mifos.connector.channel.utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionRequestValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"+254712345678", "254712345678", "+256712345678", " 254712345678 "})
    void acceptsDigitOnlyMsisdn(String msisdn) {
        assertEquals(Optional.empty(), CollectionRequestValidator.validate(request(msisdn, "100")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1315.5", "9.5", "100", "0.01"})
    void acceptsPositiveAmountsIncludingDecimals(String amount) {
        // whole-number rules are scheme specific and enforced by the scheme connector (e.g. M-Pesa)
        assertEquals(Optional.empty(), CollectionRequestValidator.validate(request("254712345678", amount)));
    }

    @Test
    void acceptsMsisdnInSecondPosition() {
        JSONObject body = request("254712345678", "100");
        JSONArray payer = new JSONArray()
                .put(party("ACCOUNTID", "123"))
                .put(party("MSISDN", "254712345678"));
        body.put("payer", payer);
        assertEquals(Optional.empty(), CollectionRequestValidator.validate(body));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "+null", "null", "+", "2547abc", "+254 712 345 678", "1234567890123456"})
    void rejectsInvalidMsisdn(String msisdn) {
        assertRejected(request(msisdn, "100"), "MSISDN");
    }

    @Test
    void rejectsNullMsisdn() {
        JSONObject body = request("254712345678", "100");
        body.getJSONArray("payer").getJSONObject(0).put("value", JSONObject.NULL);
        assertRejected(body, "string key and value");
    }

    @Test
    void rejectsPayerWithoutMsisdn() {
        JSONObject body = request("254712345678", "100");
        body.put("payer", new JSONArray().put(party("ACCOUNTID", "123")).put(party("FOO", "bar")));
        assertRejected(body, "MSISDN is missing");
    }

    @Test
    void rejectsSinglePayerEntry() {
        JSONObject body = request("254712345678", "100");
        body.put("payer", new JSONArray().put(party("MSISDN", "254712345678")));
        assertRejected(body, "Payer must contain");
    }

    @Test
    void rejectsMissingPayer() {
        JSONObject body = request("254712345678", "100");
        body.remove("payer");
        assertRejected(body, "Payer must contain");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "0", "-5"})
    void rejectsInvalidAmount(String amount) {
        assertRejected(request("254712345678", amount), "Amount");
    }

    @Test
    void rejectsMissingAmount() {
        JSONObject body = request("254712345678", "100");
        body.remove("amount");
        assertRejected(body, "Amount is missing");
    }

    @Test
    void rejectsNullAmount() {
        JSONObject body = request("254712345678", "100");
        body.getJSONObject("amount").put("amount", JSONObject.NULL);
        assertRejected(body, "Amount is missing");
    }

    private static void assertRejected(JSONObject body, String expectedMessagePart) {
        Optional<String> error = CollectionRequestValidator.validate(body);
        assertTrue(error.isPresent(), "expected rejection for " + body);
        assertTrue(error.get().contains(expectedMessagePart), error.get());
    }

    private static JSONObject request(String msisdn, String amount) {
        return new JSONObject()
                .put("payer", new JSONArray().put(party("MSISDN", msisdn)).put(party("ACCOUNTID", "123")))
                .put("amount", new JSONObject().put("amount", amount).put("currency", "KES"));
    }

    private static JSONObject party(String key, String value) {
        return new JSONObject().put("key", key).put("value", value);
    }
}
