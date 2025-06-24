package org.mifos.connector.channel.zeebe;

public class ZeebeVariables {

    private ZeebeVariables() {
    }

    public static final String ACCOUNT = "account";
    public static final String AUTH_RETRIES_LEFT = "authRetriesLeft";
    public static final String CHANNEL_REQUEST = "channelRequest";
    public static final String MPESA_CHANNEL_REQUEST = "mpesaChannelRequest";
    public static final String ERROR_INFORMATION = "errorInformation";
    public static final String IS_AUTHORISATION_REQUIRED = "isAuthorisationRequired";
    public static final String IS_RTP_REQUEST = "isRtpRequest";
    public static final String OPERATOR_MANUAL_OVERRIDE = "operatorManualOverride"; // TODO validate in request?
    public static final String ORIGIN_DATE = "originDate";
    public static final String PARTY_ID = "partyId";
    public static final String PARTY_ID_TYPE = "partyIdType";
    public static final String TENANT_ID = "tenantId";
    public static final String TRANSACTION_ID = "transactionId";
    public static final String TRANSACTION_TYPE = "transactionType";
    public static final String GSMA_AUTHORIZATION_CODE = "gsmaAuthorizationCode";
    public static final String GSMA_CHANNEL_REQUEST = "gsmaChannelRequest";
    public static final String SAMPLED_TX_IDS = "sampledTransactionIds";
    public static final String AMS = "ams";

    public static final String NOTE = "note";
    public static final String PAYMENT_SCHEME = "paymentScheme";
    public static final String CLIENT_CORRELATION_ID = "clientCorrelationId";
    public static final String CALLBACK_URL = "callbackUrl";
    public static final String WEBHOOK_PAYLOAD = "webhookPayload";
    public static final String EXTERNAL_ID = "externalId";
    public static final String AMOUNT = "amount";
    public static final String CURRENCY = "currency";
    public static final String IS_NOTIFICATIONS_SUCCESS_ENABLED = "isNotificationsSuccessEnabled";
    public static final String IS_NOTIFICATIONS_FAILURE_ENABLED = "isNotificationsFailureEnabled";
    public static final String VIRTUAL_ACCOUNT_NUMBER = "virtualAccountNumber";
}
