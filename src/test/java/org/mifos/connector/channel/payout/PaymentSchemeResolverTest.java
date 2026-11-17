package org.mifos.connector.channel.payout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {"12", "abc", "+999123456789"})
    void resolve_unparsableMsisdn_shouldUseTheDefault(String msisdn) {
        assertEquals(PaymentSchemeResolver.DEFAULT_PAYMENT_SCHEME, resolver.resolve(null, msisdn));
    }

    @Test
    void resolve_numberWithoutCarrier_shouldUseTheDefault() {
        // a Nairobi landline: a valid number that no mobile carrier owns
        assertEquals(PaymentSchemeResolver.DEFAULT_PAYMENT_SCHEME, resolver.resolve(null, "254202222222"));
    }

    @Test
    void resolve_carrierWithoutScheme_shouldUseTheDefault() {
        var withoutMtn = new PaymentSchemeResolver(Map.of("safaricom", "mpesa"));
        assertEquals(PaymentSchemeResolver.DEFAULT_PAYMENT_SCHEME, withoutMtn.resolve(null, "256772123456"));
    }
}
