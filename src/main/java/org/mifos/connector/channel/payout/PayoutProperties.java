package org.mifos.connector.channel.payout;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * {@code payout.*}: settings of {@code POST /channel/payout}.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "payout")
public class PayoutProperties {

    // Keep disabled until a payout flow is deployed
    private boolean enabled;

    // Workflow to start; {ps} is the payment scheme, {ams} the account system and {dfspid} the tenant
    private String flow = "{ps}_payout_{ams}-{dfspid}";

    // First wait for the provider's payout result before checking its status (ISO-8601 duration)
    private String timer = "PT2M";

    // How long a transaction id stays claimed: a resend within this time cannot start a second payout
    private Duration claimTtl = Duration.ofDays(30);

    String workflowFor(String paymentScheme, String ams, String tenantId) {
        return flow.replace("{ps}", paymentScheme).replace("{ams}", ams).replace("{dfspid}", tenantId);
    }
}
