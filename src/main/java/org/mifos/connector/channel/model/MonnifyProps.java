package org.mifos.connector.channel.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * DTO for Monnify payment properties.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "monnify")
public class MonnifyProps extends PosPaymentProps {
}
