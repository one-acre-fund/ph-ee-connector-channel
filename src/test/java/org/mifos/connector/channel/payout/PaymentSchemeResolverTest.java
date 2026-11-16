package org.mifos.connector.channel.payout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentSchemeResolverTest {

    private final PaymentSchemeResolver resolver = new PaymentSchemeResolver(
            Map.of("safaricom", "mpesa", "mtn", "momo", "airtel", "airtel"));

    @Test
    void resolve_withHeader_shouldUseIt() {
        assertEquals("momo", resolver.resolve(" MOMO ", "254712345678"));
    }

    @ParameterizedTest
    @CsvSource({"254712345678, mpesa", "+256772123456, momo", "256752123456, airtel"})
    void resolve_withoutHeader_shouldUseTheCarrier(String msisdn, String expected) {
        assertEquals(expected, resolver.resolve(null, msisdn));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void resolve_withoutHeaderOrMsisdn_shouldUseTheDefault(String msisdn) {
        assertEquals(PaymentSchemeResolver.DEFAULT_PAYMENT_SCHEME, resolver.resolve("", msisdn));
    }

    @Test
    void resolve_unparsableMsisdn_shouldUseTheDefault() {
        assertEquals(PaymentSchemeResolver.DEFAULT_PAYMENT_SCHEME, resolver.resolve(null, "12"));
    }
}
