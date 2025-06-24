package org.mifos.connector.channel.model;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

/**
 * DTO for Generic POS payment properties.
 */
@Getter
@Setter
public class PosPaymentProps {
    @NotBlank
    private String secretKey;

    @NotBlank
    private String amsIdentifier;

    @NotBlank
    private String amsValue;
    @NotBlank
    private String payerIdType;

    @NotBlank
    private String flow;

    private boolean successNotificationsEnabled;
    private boolean failureNotificationsEnabled;
}
