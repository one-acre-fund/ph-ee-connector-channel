package org.mifos.connector.channel.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** * DTO for Squad transaction response list.
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SquadTransactionResponseList {
    private List<SquadTransactionResponse> squadTransactionResponses;
}
