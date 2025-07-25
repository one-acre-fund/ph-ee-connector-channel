package org.mifos.connector.channel.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * DTO for Squad webhook log response.
 */
@Getter
@Setter
public class SquadWebhookLogResponse {
    private int status;
    private boolean success;
    private String message;
    private Data data;

    /**
     * DTO for Squad webhook log response data.
     */
    @Getter
    @Setter
    public static class Data {
        private long count;
        private List<Row> rows;
    }

    /**
     * DTO for Squad webhook log response row.
     */
    @Getter
    @Setter
    public static class Row {
        private String id;
        private SquadTransactionRequest payload;

        @JsonProperty("transaction_ref")
        private String transactionRef;
    }

}
