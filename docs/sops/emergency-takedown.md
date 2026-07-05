# Emergency Takedown SOP

## Purpose

Pause risky campaigns quickly while preserving evidence and member trust.

## Triggers

- Incorrect prize quantity, probability, price, or last-prize rule.
- Unauthorized image, logo, brand, character, or official-product claim.
- Age-restricted product published without complete age disclosure.
- Payment, draw, ticket, prize ownership, or shipment integrity issue.
- Legal, consumer-protection, or public safety concern.

## Immediate Actions

1. Pause the affected campaign in admin.
2. Stop related paid promotion or public announcement.
3. Preserve audit logs, campaign data, ticket state, draw orders, payment orders,
   raw webhook payloads, and screenshots of public copy.
4. Assign an incident owner and customer-support owner.
5. Publish a customer-facing notice if members could be affected.

## Investigation

- Run payment reconciliation if payment state may be involved.
- Compare campaign prize/ticket counts with draw orders and user prizes.
- Identify affected member ids, draw order ids, payment order ids, and shipment
  ids.
- Decide whether to resume, create a correction version, refund, compensate, or
  permanently retire the campaign.

## Restart Criteria

- Root cause is understood.
- Corrected campaign data is reviewed by operations and, when needed, legal.
- Member remediation plan is approved.
- Smoke test and affected flow checks pass.
- Customer-support macros are ready before reopening traffic.
