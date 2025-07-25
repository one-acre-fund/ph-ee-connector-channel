package org.mifos.connector.channel.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Max;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

/**
 * DTO for Squad payment properties.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "squad")
public class SquadProps extends PosPaymentProps {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String logsEndpoint;

    @Positive
    @Max(100)
    private int logsPageSize;

    @NotBlank
    private String token;
}
