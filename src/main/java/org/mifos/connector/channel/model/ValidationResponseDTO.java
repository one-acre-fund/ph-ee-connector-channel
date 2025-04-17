package org.mifos.connector.channel.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.mifos.connector.common.gsma.dto.CustomData;

import java.util.List;

@Getter
@Setter
public class ValidationResponseDTO {
    @JsonProperty("transactionId")
    public String transactionId;
    @JsonProperty("accountHoldingInstitutionId")
    public String accountHoldingInstitutionId;
    @JsonProperty("amount")
    public String amount;
    @JsonProperty("currency")
    public String currency;
    @JsonProperty("msisdn")
    public String msisdn;
    @JsonProperty("reconciled")
    public boolean reconciled;
    @JsonProperty("amsName")
    public String amsName;
    @JsonProperty("clientName")
    private String clientName;
    @JsonProperty("customData")
    private List<CustomData> customData;
    private String message;
}