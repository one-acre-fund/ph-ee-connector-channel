package org.mifos.connector.channel.camel.config;

public class CamelProperties {

    private CamelProperties() {}

    public static final String AUTH_TYPE = "authType";
    public static final String BATCH_ID = "batchId";
    public static final String PARTY_LOOKUP_FAILED = "partyLookupFailed";
    public static final String CLIENTCORRELATIONID = "X-CorrelationID";
    public static final String BATCH_ID_HEADER = "X-BatchID";
    public static final String PAYMENT_SCHEME_HEADER = "X-Payment-Scheme";
    public static final String SQUAD_SIGNATURE_HEADER = "x-squad-signature";
    public static final String SQUAD_PROVIDER_NAME = "squad";
    public static final String HMAC_SHA512 = "HmacSHA512";
    public static final String MONNIFY_SIGNATURE_HEADER = "monnify-signature";
    public static final String MONNIFY_PROVIDER_NAME = "monnify";
}
