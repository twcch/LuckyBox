# Refund And Compensation SOP

## Purpose

Ensure refunds, point adjustments, and compensation are reviewed, auditable, and
consistent with wallet/payment state.

## Standard Flow

1. Customer support summarizes the case with member id, payment order id, draw
   order id, shipment id, campaign id, evidence, and requested action.
2. Operator verifies current payment, wallet ledger, draw, prize, and shipment
   state.
3. Create an admin approval request for refund, point adjustment, or
   compensation.
4. Super admin reviews and either approves or rejects with reason.
5. After approval, verify resulting payment order status, wallet ledger rows,
   audit log, and member-facing balance.
6. Notify the member.

## Refund Rules

- Refunds require a paid order and a clear reason.
- Do not refund if the wallet balance cannot safely roll back the credited
  points; use manual review and compensation instead.
- Real provider money movement must match LuckyBox payment order state once the
  real provider integration is active.

## Compensation Rules

- Use the smallest action that resolves the member harm.
- Prefer clearly labeled bonus points or coupon compensation when cash refund is
  not required.
- Do not compensate to hide a campaign disclosure or legal issue; escalate for
  takedown/legal review.

## Done Criteria

- Approval request is resolved.
- Payment order, wallet ledger, audit log, and support note agree.
- Member notification includes outcome and support reference.
