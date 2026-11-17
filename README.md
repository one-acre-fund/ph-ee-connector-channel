# payment-hub-ee
Payment Hub Enterprise Edition middleware for integration to real-time payment systems. 
#Auto-Trigger

## Payouts to mobile money wallets
`POST /channel/payout` pays money out to a customer's wallet on any mobile money scheme. The channel only checks what
is true for every scheme; each scheme connector (mpesa, momo, airtel, ...) checks its own rules and calls its provider.

Request (headers `Platform-TenantId`, `X-CorrelationID`, optional `X-Payment-Scheme`):
```json
{
  "payee": [{"key": "MSISDN", "value": "256771234567"}, {"key": "FINERACTACCOUNTID", "value": "123456"}],
  "amount": {"amount": "5000", "currency": "UGX"},
  "remarks": "Refund of overpayment",
  "customData": [{"key": "callbackUrl", "value": "https://..."}]
}
```
Disabled unless `PAYOUT_ENABLED=true`. The transaction id is derived from the tenant and `X-CorrelationID` (a
deterministic version 4 UUID), so a resent request gets the same id. Before starting the workflow the channel claims
that id in Redis (`SET NX payout:{transactionId}`, `payout.claim-ttl`, 30 days), so a resent request can never start a
second payout, whichever pod receives it. Payouts need Redis (`redis.*`); without it they are refused and nothing starts.

| HTTP | `status` | Meaning |
|---|---|---|
| 202 | `STARTED` | The payout workflow was started. Body: `{"transactionId", "status"}` |
| 409 | `DUPLICATE` | This `X-CorrelationID` was already submitted; nothing new was started. Carries the `transactionId` |
| 503 | `UNCONFIRMED` | The start was sent to Zeebe but not confirmed: the payout may or may not be running. Carries the `transactionId`; check it before retrying. Resending the same `X-CorrelationID` cannot start a second payout |
| 400 / 403 / 404 / 503 | `REJECTED` | Nothing was started: invalid request, payouts disabled, flow not deployed (the claim is released, so the request can be resent once it is), or Redis unreachable |

Routing:
- Payment scheme `{ps}`: the `X-Payment-Scheme` header, otherwise the carrier of the payee MSISDN mapped through
  `payment-schemes` (`safaricom -> mpesa`, `mtn -> momo`, `airtel -> airtel`).
  **Fallback: `mpesa`.** When the MSISDN cannot be parsed, has no known carrier (e.g. a ported number, a new prefix or a
  landline), or its carrier is not in `payment-schemes`, the payout is routed to `mpesa`. The carrier lookup uses
  libphonenumber's prefix data, which does not know about number portability. Send `X-Payment-Scheme` whenever the
  scheme is known, and always for any scheme other than M-Pesa, so a payout is never sent to the wrong provider.
- AMS `{ams}`: the key of the payee's non-MSISDN entry, mapped through `ams.groups` (as for collections).
- Workflow: `payout.flow` (`PAYOUT_FLOW`), default `{ps}_payout_{ams}-{dfspid}`, e.g. `mpesa_payout_fineract-kenya`,
  `momo_payout_fineract-uganda`. A scheme with no deployed flow answers `404`.

### Contract for a scheme connector
Adding payouts for a new provider needs a BPMN `{ps}_payout_{ams}-{dfspid}` and these workers in its connector. Job
types carry the scheme so connectors never take each other's jobs.

Process variables set by the channel: `transactionId`, `tenantId`, `ams`, `paymentScheme`, `clientCorrelationId`,
`phoneNumber`, `accountId`, `amount`, `currency`, `remarks` (may be empty), `customData`, `payoutRequest` (the raw
request JSON), `payoutTimer`, `originDate`.

| Job type | Completes with |
|---|---|
| `init-{ps}-payout` | `payoutFailed=true` (+ `errorCode`, `errorDescription`, `errorInformation`) only when nothing can have been paid; otherwise `payoutFailed=false` and the flow waits for `payout-result`. Use `transactionId` as the provider's idempotency key (M-Pesa `OriginatorConversationID`, MTN `X-Reference-Id`). |
| `get-{ps}-payout-status` | Queries the provider when no result arrived in time; publishes `payout-result` once the outcome is final, otherwise sets `payoutStatusChecksExhausted` after the last check. |

Message `payout-result`, correlation key `transactionId`, message id `payout-result-{transactionId}` (published at most
once), variables `payoutFailed` and, on success, `externalId` (the provider's receipt). An outcome that is unknown is
never failed automatically: an operator resolves it with `POST /channel/transaction/{transactionId}/resolve`.
