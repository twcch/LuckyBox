# Customer Support SOP

## Purpose

Give support staff a consistent process for member help, privacy-safe account
review, compensation requests, and escalation.

## Roles

- Customer support: first response, ticket notes, member lookup, issue summary.
- Operator: shipment/campaign/payment context and operational correction.
- Super admin: approval for high-risk point adjustment, refund, or compensation.
- Engineer: data integrity investigation, failed webhook/reconciliation issue,
  incident response.

## Standard Flow

1. Identify the member by account email, order id, draw order id, shipment id, or
   payment merchant trade number.
2. Open the relevant admin page and read only the information needed for the
   case.
3. Add an internal support note when the case changes member state or needs
   follow-up.
4. For point adjustment, refund, or compensation, create an approval request
   instead of executing directly.
5. Notify the member with the final result, support owner, and next expected
   action.

## Privacy Rules

- Do not export full member lists or raw personal data into chat tools.
- Do not reveal another member's email, phone, address, prize, payment, or draw
  history.
- Use admin detail pages for case review so audit logs capture sensitive access.
- If a member requests deletion or legal data handling, escalate to the business
  owner before changing records.

## Escalation

Escalate immediately when any of these are true:

- Payment paid but points not credited.
- Draw succeeded but ticket/prize ownership looks inconsistent.
- Duplicate shipment, lost package, product defect, or prize mismatch.
- Campaign disclosure, IP-rights, minor-use, or consumer-protection concern.
- Member threatens chargeback, legal complaint, or public escalation.

## Done Criteria

- Member-facing reply sent.
- Admin note or audit trail exists.
- Approval request is resolved when money/points/prizes changed.
- Related payment, wallet ledger, shipment, draw order, and campaign state are
  consistent.
