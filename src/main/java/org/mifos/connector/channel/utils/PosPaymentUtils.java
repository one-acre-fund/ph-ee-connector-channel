package org.mifos.connector.channel.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mifos.connector.channel.camel.config.CamelProperties.HMAC_SHA512;

/**
 * Utility class for POS payment operations.
 */
public class PosPaymentUtils {
    private PosPaymentUtils() {
    }

    public static boolean isValidSignature(String signature, String secretKey, String payload) throws NoSuchAlgorithmException, InvalidKeyException {
        if (isBlank(signature) || isBlank(secretKey) || isBlank(payload)) {
            return false;
        }
        byte[] byteKey = secretKey.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec keySpec = new SecretKeySpec(byteKey, HMAC_SHA512);
        Mac sha512HMAC = Mac.getInstance(HMAC_SHA512);
        sha512HMAC.init(keySpec);
        byte[] macData = sha512HMAC.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        String result = String.format("%040x", new BigInteger(1, macData));
        return result.equals(signature);
    }
}
