package org.mifos.connector.channel.payout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mifos.connector.channel.model.Payer;
import org.mifos.connector.channel.model.PayoutRequestDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mifos.connector.channel.payout.PayoutTestData.party;
import static org.mifos.connector.channel.payout.PayoutTestData.request;

class PayoutRequestValidatorTest {

    @Test
    void validate_validRequest_shouldPass() {
        assertValid(request("254712345678", "1500"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"+254712345678", "254712345678", "0712345678", "256771234567"})
    void validate_digitOnlyMsisdn_shouldPass(String msisdn) {
        // each scheme connector applies its own format, e.g. M-Pesa B2C only pays 254 numbers
        assertValid(request(msisdn, "100"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "+null", "null", "2547abc", "+254 712 345 678", "1234567890123456"})
    void validate_invalidMsisdn_shouldReject(String msisdn) {
        assertRejected(request(msisdn, "100"), "MSISDN");
    }

    @Test
    void validate_withoutMsisdn_shouldReject() {
        var request = request("254712345678", "100");
        request.setPayee(List.of(party("ACCOUNTID", "1")));
        assertRejected(request, "MSISDN is missing");
    }

    @Test
    void validate_withNullPayeeValue_shouldReject() {
        var request = request("254712345678", "100");
        request.getPayee().get(0).setValue(null);
        assertRejected(request, "key and a value");
    }

    @Test
    void validate_withoutPayee_shouldReject() {
        var request = request("254712345678", "100");
        request.setPayee(null);
        assertRejected(request, "Payee is missing");
        request.setPayee(new ArrayList<>());
        assertRejected(request, "Payee is missing");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "abc", "0", "-5"})
    void validate_invalidAmount_shouldReject(String amount) {
        assertRejected(request("254712345678", amount), "Amount");
    }

    @Test
    void validate_withoutAmount_shouldReject() {
        var request = request("254712345678", "100");
        request.setAmount(null);
        assertRejected(request, "Amount is missing");
    }

    @Test
    void validate_decimalAmount_shouldPass() {
        // whole-number rules belong to the scheme connector
        assertValid(request("254712345678", "9.5"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void validate_withoutCurrency_shouldReject(String currency) {
        var request = request("254712345678", "100");
        request.getAmount().setCurrency(currency);
        assertRejected(request, "Currency");
    }

    @ParameterizedTest
    @ValueSource(strings = {"UGX", "XOF", "ZMW"})
    void validate_anyCurrency_shouldPass(String currency) {
        // MTN, Airtel and others pay out in their market's currency; the connector checks it
        var request = request("256771234567", "5000");
        request.getAmount().setCurrency(currency);
        assertValid(request);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"x", "  y  "})
    void validate_missingOrShortRemarks_shouldPass(String remarks) {
        // remark limits differ per scheme, so each connector applies its own
        var request = request("254712345678", "100");
        request.setRemarks(remarks);
        assertValid(request);
    }

    @Test
    void validate_tooLongRemarks_shouldReject() {
        var request = request("254712345678", "100");
        request.setRemarks("x".repeat(PayoutRequestValidator.MAX_REMARKS + 1));
        assertRejected(request, "Remarks");
    }

    @Test
    void msisdnAndAccount_shouldFindEntriesRegardlessOfOrder() {
        List<Payer> payee = List.of(party("ACCOUNTID", "ACC-1"), party("msisdn", " 254712345678 "));

        assertEquals(Optional.of("254712345678"), PayoutRequestValidator.msisdn(payee));
        assertEquals("ACCOUNTID", PayoutRequestValidator.account(payee).orElseThrow().getKey());
    }

    @Test
    void transactionId_shouldBeTheSameForTheSameTenantAndCorrelationId() {
        var id = PayoutRequestValidator.transactionId("kenya", "corr-1");

        assertEquals(id, PayoutRequestValidator.transactionId("kenya", "corr-1"));
        assertNotEquals(id, PayoutRequestValidator.transactionId("kenya", "corr-2"));
        assertNotEquals(id, PayoutRequestValidator.transactionId("rwanda", "corr-1"));
        assertEquals(36, id.length());
    }

    private static void assertValid(PayoutRequestDTO request) {
        assertEquals(Optional.empty(), PayoutRequestValidator.validate(request));
    }

    private static void assertRejected(PayoutRequestDTO request, String expectedMessagePart) {
        var error = PayoutRequestValidator.validate(request);
        assertTrue(error.isPresent(), "expected a rejection");
        assertTrue(error.get().contains(expectedMessagePart), error.get());
    }
}
