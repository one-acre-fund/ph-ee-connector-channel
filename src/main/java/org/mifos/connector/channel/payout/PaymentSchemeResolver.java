package org.mifos.connector.channel.payout;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberToCarrierMapper;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * The mobile money scheme (mpesa, momo, airtel...) that serves a wallet: the {@code X-Payment-Scheme} header when
 * given, otherwise the carrier of the MSISDN mapped through {@code payment-schemes}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSchemeResolver {

    static final String DEFAULT_PAYMENT_SCHEME = "mpesa";

    @Value("#{${payment-schemes}}")
    private final Map<String, String> paymentSchemesByCarrier;

    public String resolve(String paymentSchemeHeader, String msisdn) {
        if (StringUtils.isNotBlank(paymentSchemeHeader)) {
            return paymentSchemeHeader.trim().toLowerCase(Locale.ROOT);
        }
        if (StringUtils.isBlank(msisdn)) {
            return DEFAULT_PAYMENT_SCHEME;
        }
        var number = msisdn.trim().startsWith("+") ? msisdn.trim() : "+" + msisdn.trim();
        try {
            var phone = PhoneNumberUtil.getInstance().parse(number, null);
            var carrier = PhoneNumberToCarrierMapper.getInstance().getNameForNumber(phone, Locale.ENGLISH);
            if (StringUtils.isNotBlank(carrier)) {
                return paymentSchemesByCarrier.getOrDefault(carrier.toLowerCase(Locale.ROOT), DEFAULT_PAYMENT_SCHEME);
            }
        } catch (NumberParseException e) {
            log.warn("Unable to parse MSISDN {}, using the default payment scheme {}", number, DEFAULT_PAYMENT_SCHEME);
        }
        return DEFAULT_PAYMENT_SCHEME;
    }
}
