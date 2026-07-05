# LuckyBox Launch Sign-Off Register

This register turns the remaining non-code launch questions into explicit
owners, evidence, and environment gates. It is not legal advice; it is the
engineering handoff checklist for business, legal, payment, fulfillment, and
operations sign-off.

Use this file with `docs/launch-readiness.md` and
`scripts/check-launch-readiness.sh`. Production traffic must stay blocked until
each item has a real owner, evidence link, and matching env flag in the
deployment secret store.

## Required Evidence

### Business And Legal

- Business registration is confirmed.
  - Gate: `LUCKYBOX_BUSINESS_REGISTRATION_APPROVED=true`
  - Evidence: company name, tax ID, responsible owner, invoice/legal entity
    mapping.
- Legal counsel is assigned before policy review.
  - Gate: `LUCKYBOX_LEGAL_COUNSEL_ASSIGNED=true`
  - Evidence: reviewer name or firm, review date, scope, and open questions.
- Terms, privacy, consumer-protection copy, refund/compensation copy, age/minor
  rules, probability/fairness disclosure, and campaign disclosure are reviewed.
  - Gate: `LUCKYBOX_LEGAL_REVIEW_APPROVED=true`
  - Evidence: signed review note or issue tracker links.
- Legal feedback is applied back to the public documents.
  - Gate: `LUCKYBOX_LEGAL_FEEDBACK_APPLIED=true`
  - Evidence: commit, PR, or release note showing the policy changes.

### Brand, Product, And Copy

- Product source model is approved for launch.
  - Gate: `LUCKYBOX_PRODUCT_SOURCE_APPROVED=true`
  - Evidence: official代理, 平行輸入, 自製混套, or mixed-source policy with
    required campaign labels.
- Product image, IP, and commercial-use rights are approved for the first
  official campaigns.
  - Gate: `LUCKYBOX_PRODUCT_RIGHTS_APPROVED=true`
  - Evidence: supplier authorization, license terms, invoice/procurement record,
    or other business-approved rights proof.
- Branded SEO and marketing copy is approved.
  - Gate: `LUCKYBOX_BRAND_COPY_APPROVED=true`
  - Evidence: legal/brand approval. Until approved, public UI copy should use
    generic terms such as `抽賞`, `賞池`, and `限定週邊抽賞` instead of implying an
    official branded relationship.
- First official campaign content is approved.
  - Gate: `LUCKYBOX_FIRST_OFFICIAL_CAMPAIGNS_READY=true`
  - Evidence: product list, stock count, prize images, source labels, risk
    disclosures, shipping assumption, and campaign owner approval.

### Payment And Invoice

- Payment provider contract and production merchant account are active.
  - Gate: `LUCKYBOX_PAYMENT_PROVIDER_CONTRACT_APPROVED=true`
  - Evidence: provider contract, production merchant id, dashboard callback URL,
    and staging/production callback test record.
- ECPay installment periods are approved if credit-card installment checkout is
  enabled.
  - Gate: `LUCKYBOX_PAYMENT_ECPAY_INSTALLMENT_CONTRACT_APPROVED=true`
  - Evidence: approved ECPay installment periods and sandbox payment result.
- Invoice policy is approved.
  - Gate: `LUCKYBOX_INVOICE_POLICY_APPROVED=true`
  - Evidence: manual invoice process, e-invoice provider plan, or signed decision
    that invoice automation is out of launch scope.
- ECPay production credentials are configured in the deployment secret store.
  - Gates: `LUCKYBOX_PAYMENT_ECPAY_MERCHANT_ID`,
    `LUCKYBOX_PAYMENT_ECPAY_HASH_KEY`, `LUCKYBOX_PAYMENT_ECPAY_HASH_IV`,
    `LUCKYBOX_PAYMENT_ECPAY_RETURN_URL`, and
    `LUCKYBOX_PAYMENT_ECPAY_CLIENT_BACK_URL`
  - Evidence: callback test result and payment reconciliation output.
- LINE Pay production credentials are configured if LINE Pay is selected.
  - Gates: `LUCKYBOX_PAYMENT_PROVIDER=LINEPAY`,
    `LUCKYBOX_PAYMENT_LINEPAY_ENABLED=true`,
    `LUCKYBOX_PAYMENT_LINEPAY_CHANNEL_ID`,
    `LUCKYBOX_PAYMENT_LINEPAY_CHANNEL_SECRET`,
    `LUCKYBOX_PAYMENT_LINEPAY_API_BASE_URL`, and
    `LUCKYBOX_PAYMENT_LINEPAY_CALLBACK_TESTED=true`
  - Evidence: production channel id, API base URL, confirm/cancel callback URLs,
    provider dashboard callback test result, and payment reconciliation output.
- JKo Pay production credentials are configured if JKo Pay is selected.
  - Gates: `LUCKYBOX_PAYMENT_PROVIDER=JKOPAY`,
    `LUCKYBOX_PAYMENT_JKOPAY_ENABLED=true`,
    `LUCKYBOX_PAYMENT_JKOPAY_API_KEY`,
    `LUCKYBOX_PAYMENT_JKOPAY_SECRET_KEY`,
    `LUCKYBOX_PAYMENT_JKOPAY_STORE_ID`,
    `LUCKYBOX_PAYMENT_JKOPAY_ENTRY_URL`,
    `LUCKYBOX_PAYMENT_JKOPAY_CONFIRM_URL`,
    `LUCKYBOX_PAYMENT_JKOPAY_RESULT_URL`,
    `LUCKYBOX_PAYMENT_JKOPAY_RESULT_DISPLAY_URL`, and
    `LUCKYBOX_PAYMENT_JKOPAY_CALLBACK_TESTED=true`
  - Evidence: production store id, Entry API URL, confirm/result/result-display
    callback URLs, provider dashboard callback test result, and payment
    reconciliation output.
- NewebPay production credentials are configured if NewebPay is selected as the
  fallback provider.
  - Gates: `LUCKYBOX_PAYMENT_PROVIDER=NEWEBPAY`,
    `LUCKYBOX_PAYMENT_NEWEBPAY_MERCHANT_ID`,
    `LUCKYBOX_PAYMENT_NEWEBPAY_HASH_KEY`,
    `LUCKYBOX_PAYMENT_NEWEBPAY_HASH_IV`,
    `LUCKYBOX_PAYMENT_NEWEBPAY_NOTIFY_URL`, and
    `LUCKYBOX_PAYMENT_NEWEBPAY_RETURN_URL`
  - Evidence: production merchant id, notify/return URLs, provider callback test
    result, and payment reconciliation output.

Use `scripts/generate-launch-evidence-template.sh --env .env.production --out
launch-evidence.md` to create a redacted evidence packet for these external
sign-offs. Do not store real secrets in the generated file.

### Fulfillment

- Shipping owner is assigned.
  - Gates: `LUCKYBOX_SHIPPING_OWNER_ASSIGNED=true` and
    `LUCKYBOX_SHIPPING_OWNER`
  - Evidence: named owner or team and escalation contact.
- Logistics provider is approved.
  - Gates: `LUCKYBOX_LOGISTICS_PROVIDER_APPROVED=true` and
    `LUCKYBOX_LOGISTICS_PROVIDER`
  - Evidence: provider name, service level, tracking workflow, return-package
    process, and cost assumptions.
- Convenience-store pickup policy is approved.
  - Gate: `LUCKYBOX_CONVENIENCE_STORE_PICKUP_POLICY_APPROVED=true`
  - Evidence: enabled provider/process, or signed decision that it is not
    supported at launch.
- International shipping policy is approved.
  - Gate: `LUCKYBOX_INTERNATIONAL_SHIPPING_POLICY_APPROVED=true`
  - Evidence: supported countries, customs/tax owner, or signed decision that it
    is not supported at launch.
- Preorder campaign policy is approved.
  - Gate: `LUCKYBOX_PREORDER_POLICY_APPROVED=true`
  - Evidence: whether preorder pools are allowed, required ETA disclosures, delay
    handling, and refund/compensation policy.
- Customer support, shipping, emergency takedown, and refund/compensation SOPs
  are approved.
  - Gates: `LUCKYBOX_SUPPORT_SOP_APPROVED=true`,
    `LUCKYBOX_SHIPPING_SOP_APPROVED=true`,
    `LUCKYBOX_TAKEDOWN_SOP_APPROVED=true`,
    `LUCKYBOX_REFUND_SOP_APPROVED=true`
  - Evidence: owner approval and support mailbox/tool readiness.

### Deployment And Launch

- Production deployment owner and rollback owner are assigned.
  - Gates: `LUCKYBOX_DEPLOYMENT_OWNER` and `LUCKYBOX_ROLLBACK_OWNER`
  - Evidence: release owner, rollback owner, deployment window, and rollback
    command/runbook.
- Production smoke test passes after deployment.
  - Gate: `LUCKYBOX_PRODUCTION_SMOKE_TEST_DONE=true`
  - Evidence: `scripts/smoke-test.sh https://production-origin` output.
- Small traffic test is completed.
  - Gate: `LUCKYBOX_SMALL_TRAFFIC_TEST_DONE=true`
  - Evidence: monitored test window, order/payment/draw/shipment samples,
    incident log, and go/no-go decision.
- Launch checklist is approved.
  - Gate: `LUCKYBOX_LAUNCH_CHECKLIST_APPROVED=true`
  - Evidence: final approval note and launch announcement owner.

## Legal Feedback Log

Record legal-review changes here before setting
`LUCKYBOX_LEGAL_FEEDBACK_APPLIED=true`.

| Date | Reviewer | File/Section | Required Change | Applied In | Owner |
| --- | --- | --- | --- | --- | --- |
| Pending external review | Legal counsel | Public policies and launch disclosures | Record requested edits after counsel review | Pending | Launch owner |
