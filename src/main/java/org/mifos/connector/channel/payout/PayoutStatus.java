package org.mifos.connector.channel.payout;

/**
 * What {@code POST /channel/payout} did with a request.
 */
public enum PayoutStatus {

    // The payout workflow was started (202)
    STARTED,

    // A payout with this transaction id was already submitted; nothing new was started (409)
    DUPLICATE,

    // The start was sent but not confirmed: the payout may or may not be running. A resend with the same
    // X-CorrelationID cannot start a second one; check the transaction id before using a new one (503)
    UNCONFIRMED,

    // Nothing was started: invalid request, payouts disabled, flow not deployed or duplicate check unavailable
    REJECTED
}
