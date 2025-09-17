package org.mifos.connector.channel.service;

import org.mifos.connector.channel.model.SquadTransactionResponseList;
import org.mifos.connector.channel.model.SquadTransactionsSyncRequest;

public interface SquadTransactionService {

    /**
     * Synchronizes Squad transactions by fetching missed Squad webhook logs and reconciling the transactions.
     *
     * @param transactionsSyncRequest JSON request body.
     * @return {@link SquadTransactionResponseList} with the result of the synchronization.
     */
    SquadTransactionResponseList syncTransactions(SquadTransactionsSyncRequest transactionsSyncRequest);
}
