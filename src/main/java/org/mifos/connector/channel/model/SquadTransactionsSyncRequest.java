package org.mifos.connector.channel.model;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * DTO for Squad transactions synchronization request.
 */
@Getter
@Setter
public class SquadTransactionsSyncRequest {
    @NotBlank
    private String correlationId;

    private List<@NotBlank String> districtTokens;
}
