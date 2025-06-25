package org.mifos.connector.channel.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

import static org.mifos.connector.channel.camel.config.CamelProperties.AMS_ID_VALUE_PLACEHOLDER;
import static org.mifos.connector.channel.camel.config.CamelProperties.MONNIFY_PROVIDER_NAME;
import static org.mifos.connector.channel.camel.config.CamelProperties.SQUAD_PROVIDER_NAME;

/**
 * DTO representing a lightweight channel request payload.
 */
@Getter
@Setter
@AllArgsConstructor
public class LiteChannelRequest {
    private PartyData payer;
    private PartyData payee;
    private Amount amount;
    private TransactionType transactionType;

    @Getter
    @Setter
    @AllArgsConstructor
    public static class PartyData {
        private PartyIdInfo partyIdInfo;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class PartyIdInfo {
        private String partyIdType;
        private String partyIdentifier;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class Amount {
        private BigDecimal amount;
        private String currency;
    }

    /**
     * Creates a {@link LiteChannelRequest} from a {@link SquadTransactionRequest}.
     * @param request {@link SquadTransactionRequest}
     * @param squadProps {@link SquadProps} containing configuration properties for Squad payments
     * @return {@link LiteChannelRequest} populated with data from the Squad transaction request
     */
    public static LiteChannelRequest fromSquadTransactionRequest(SquadTransactionRequest request, SquadProps squadProps) {
        PartyIdInfo payerPartyIdInfo = new PartyIdInfo(squadProps.getPayerIdType(), request.getVirtualAccountNumber());
        PartyData payer = new PartyData(payerPartyIdInfo);

        PartyIdInfo payeePartyIdInfo = new PartyIdInfo(squadProps.getAmsIdentifier(), AMS_ID_VALUE_PLACEHOLDER);
        PartyData payee = new PartyData(payeePartyIdInfo);

        Amount amount = new Amount(request.getPrincipalAmount(), request.getCurrency());

        TransactionType transactionType = new TransactionType();
        transactionType.setScenario(SQUAD_PROVIDER_NAME);

        return new LiteChannelRequest(payer, payee, amount, transactionType);
    }

    /**
     * Creates a {@link LiteChannelRequest} from a {@link MonnifyTransactionRequest.EventData}.
     * @param requestData {@link MonnifyTransactionRequest.EventData} containing transaction details
     * @param monnifyProps {@link MonnifyProps} containing configuration properties for Monnify payments
     * @return {@link LiteChannelRequest} populated with data from the Monnify transaction request
     */
    public static LiteChannelRequest fromMonnifyTransactionRequest(MonnifyTransactionRequest.EventData requestData, MonnifyProps monnifyProps) {
        PartyIdInfo payerPartyIdInfo = new PartyIdInfo(monnifyProps.getPayerIdType(), requestData.getPaymentReference());
        PartyData payer = new PartyData(payerPartyIdInfo);

        PartyIdInfo payeePartyIdInfo = new PartyIdInfo(monnifyProps.getAmsIdentifier(), AMS_ID_VALUE_PLACEHOLDER);
        PartyData payee = new PartyData(payeePartyIdInfo);

        Amount amount = new Amount(requestData.getAmountPaid(), requestData.getCurrency());

        TransactionType transactionType = new TransactionType();
        transactionType.setScenario(MONNIFY_PROVIDER_NAME);

        return new LiteChannelRequest(payer, payee, amount, transactionType);
    }
}
