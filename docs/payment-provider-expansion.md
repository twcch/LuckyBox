# Payment Provider Expansion

This document records Phase 2 payment expansion beyond the first ECPay card
checkout. It separates provider-specific engineering work from merchant account,
contract, and production verification work that cannot be completed inside the
repository.

## Current Status

- ECPay one-time credit-card checkout: implemented.
- ECPay credit-card installment checkout: implemented through optional
  `CreditInstallment` AioCheckOut field.
- LINE Pay redirect checkout: implemented. Production use still requires a LINE
  Pay merchant account, channel credentials, provider dashboard callback test,
  and `LUCKYBOX_PAYMENT_LINEPAY_CALLBACK_TESTED=true`.
- JKo Pay redirect checkout: implemented. Production use still requires a JKo
  Pay merchant account, API credentials, provider dashboard callback test, and
  `LUCKYBOX_PAYMENT_JKOPAY_CALLBACK_TESTED=true`.

## ECPay Credit Installment

ECPay installment support uses the same AioCheckOut endpoint and callback flow
as the existing ECPay adapter.

Required settings when enabled:

- `LUCKYBOX_PAYMENT_PROVIDER=ECPAY`
- `LUCKYBOX_PAYMENT_ECPAY_ENABLED=true`
- `LUCKYBOX_PAYMENT_ECPAY_CHOOSE_PAYMENT=Credit`
- `LUCKYBOX_PAYMENT_ECPAY_CREDIT_INSTALLMENT=3,6`
- `LUCKYBOX_PAYMENT_ECPAY_INSTALLMENT_CONTRACT_APPROVED=true`

Allowed values for `LUCKYBOX_PAYMENT_ECPAY_CREDIT_INSTALLMENT`:

- One or more approved periods from `3,6,12,18,24`
- `30N` for flexible installment

Launch notes:

- The periods must be approved by ECPay before use.
- The backend includes `CreditInstallment` in the signed checkout fields only
  when the env value is present.
- The readiness script rejects invalid installment period values and requires
  installment contract approval when installment is enabled.

## LINE Pay Adapter

LINE Pay uses a request and confirm flow instead of ECPay form POST:

1. Merchant server calls the LINE Pay payment request API.
2. LINE Pay returns a transaction id and redirect/payment URL.
3. Customer authenticates with LINE Pay.
4. Merchant server confirms the payment using the transaction id, amount, and
   currency.
5. LuckyBox marks the payment order paid only after a successful confirm result.

Implemented LuckyBox endpoints:

- `POST /api/account/payment-orders/{orderId}/linepay-checkout`
- `GET /api/webhooks/payment/linepay/confirm/{merchantTradeNo}`
- `GET /api/webhooks/payment/linepay/cancel/{merchantTradeNo}`

Required settings:

- `LUCKYBOX_PAYMENT_PROVIDER=LINEPAY`
- `LUCKYBOX_PAYMENT_LINEPAY_ENABLED=true`
- `LUCKYBOX_PAYMENT_LINEPAY_CHANNEL_ID`
- `LUCKYBOX_PAYMENT_LINEPAY_CHANNEL_SECRET`
- `LUCKYBOX_PAYMENT_LINEPAY_API_BASE_URL`
- `LUCKYBOX_PAYMENT_LINEPAY_CALLBACK_TESTED=true`

The backend signs request and confirm calls with LINE Pay Online API v3 HMAC
headers. It writes the request response to `provider_payload`, uses the LINE Pay
transaction id as the confirm event id, and treats repeated confirm redirects as
idempotent.

## JKo Pay Adapter

JKo Pay online payment uses an Entry API to obtain a `payment_url`, with optional
merchant confirm URL and required result URL callback:

1. Merchant server calls the Entry API with a unique platform order id.
2. JKo Pay returns `payment_url` and, for desktop flows, QR code data.
3. JKo Pay may call merchant `confirm_url` before payment.
4. JKo Pay calls merchant `result_url` after successful payment.
5. LuckyBox marks the payment order paid only after a valid result callback.

Implemented LuckyBox endpoints:

- `POST /api/account/payment-orders/{orderId}/jkopay-checkout`
- `POST /api/webhooks/payment/jkopay/confirm`
- `POST /api/webhooks/payment/jkopay/result`

Required settings:

- `LUCKYBOX_PAYMENT_PROVIDER=JKOPAY`
- `LUCKYBOX_PAYMENT_JKOPAY_ENABLED=true`
- `LUCKYBOX_PAYMENT_JKOPAY_API_KEY`
- `LUCKYBOX_PAYMENT_JKOPAY_SECRET_KEY`
- `LUCKYBOX_PAYMENT_JKOPAY_STORE_ID`
- `LUCKYBOX_PAYMENT_JKOPAY_ENTRY_URL`
- `LUCKYBOX_PAYMENT_JKOPAY_CONFIRM_URL`
- `LUCKYBOX_PAYMENT_JKOPAY_RESULT_URL`
- `LUCKYBOX_PAYMENT_JKOPAY_RESULT_DISPLAY_URL`
- `LUCKYBOX_PAYMENT_JKOPAY_CALLBACK_TESTED=true`

The backend signs Entry API request bodies with JKo Pay's HMAC-SHA256 digest
header. The confirm URL returns whether the LuckyBox order is still pending, and
the result URL validates trade number, amount, success status, and duplicate
delivery before crediting points.

## Production Rule

`scripts/check-launch-readiness.sh` accepts `ECPAY`, `NEWEBPAY`, `LINEPAY`, or
`JKOPAY`, but LINE Pay and JKo Pay require explicit callback-tested flags in
addition to provider credentials and the shared payment-provider contract gate.
Do not set those flags until the real merchant dashboard callback flow has been
verified against the deployed production URL.
