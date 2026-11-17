package org.mifos.connector.channel.payout;

import org.mifos.connector.channel.model.Amount;
import org.mifos.connector.channel.model.Payer;
import org.mifos.connector.channel.model.PayoutRequestDTO;

import java.util.ArrayList;
import java.util.List;

final class PayoutTestData {

    private PayoutTestData() {
    }

    static PayoutRequestDTO request(String msisdn, String amount) {
        var money = new Amount();
        money.setAmount(amount);
        money.setCurrency("KES");
        var request = new PayoutRequestDTO();
        request.setPayee(new ArrayList<>(List.of(party("MSISDN", msisdn), party("FINERACTACCOUNTID", "ACC-1"))));
        request.setAmount(money);
        request.setRemarks("Refund of overpayment");
        return request;
    }

    static Payer party(String key, String value) {
        var party = new Payer();
        party.setKey(key);
        party.setValue(value);
        return party;
    }
}
