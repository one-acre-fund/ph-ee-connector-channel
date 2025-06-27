package org.mifos.connector.channel.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * DTO for Squad payment properties.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "squad")
public class SquadProps extends PosPaymentProps {

}
