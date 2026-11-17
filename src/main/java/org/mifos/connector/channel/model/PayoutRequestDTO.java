package org.mifos.connector.channel.model;

import lombok.Getter;
import lombok.Setter;
import org.mifos.connector.common.gsma.dto.CustomData;

import java.util.List;

/**
 * Request to pay money out to a customer's mobile money wallet, on any scheme (M-Pesa, MTN MoMo, Airtel...).
 */
@Getter
@Setter
public class PayoutRequestDTO {
    // The customer to pay: an MSISDN entry and the customer's account identifier in the AMS
    private List<Payer> payee;
    // Amount and currency; the scheme connector checks the currency and amount limits of its market
    private Amount amount;
    // Optional note shown to the customer; each connector trims it to what its provider accepts
    private String remarks;
    private List<CustomData> customData;
}
