#!/usr/bin/env bash
set -euo pipefail

ENV_FILE=""
OUT_FILE=""

usage() {
  cat <<'EOF'
Usage: scripts/generate-launch-evidence-template.sh [--env FILE] [--out FILE]

Generates a Markdown evidence template for the external LuckyBox launch sign-offs.
It does not approve anything; it creates the owner/evidence fields that business,
legal, payment, fulfillment, and deployment owners must fill before launch.

Options:
  --env FILE   Source an env file and include a redacted env snapshot.
  --out FILE   Write the template to FILE instead of stdout.
  -h, --help   Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)
      ENV_FILE="${2:-}"
      shift 2
      ;;
    --out)
      OUT_FILE="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -n "$ENV_FILE" ]]; then
  if [[ ! -f "$ENV_FILE" ]]; then
    echo "Env file not found: $ENV_FILE" >&2
    exit 1
  fi
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

value_of() {
  local name="$1"
  printf '%s' "${!name-}"
}

display_value() {
  local name="$1"
  local value
  value="$(value_of "$name")"
  if [[ -z "$value" ]]; then
    printf '_unset_'
  elif [[ "$name" == *SECRET* || "$name" == *HASH_KEY* || "$name" == *HASH_IV* || "$name" == *PASSWORD* || "$name" == *API_KEY* || "$name" == *DSN* ]]; then
    printf '_redacted_'
  else
    printf '%s' "$value"
  fi
}

emit_gate_rows() {
  local -a names=(
    SPRING_PROFILES_ACTIVE
    LUCKYBOX_PAYMENT_PROVIDER
    LUCKYBOX_PAYMENT_MOCK_ENABLED
    LUCKYBOX_APP_BASE_URL
    LUCKYBOX_DOMAIN_NAME
    LUCKYBOX_DB_URL
    LUCKYBOX_UPLOAD_DIR
    LUCKYBOX_PAYMENT_PROVIDER_CONTRACT_APPROVED
    LUCKYBOX_BUSINESS_REGISTRATION_APPROVED
    LUCKYBOX_LEGAL_COUNSEL_ASSIGNED
    LUCKYBOX_LEGAL_REVIEW_APPROVED
    LUCKYBOX_LEGAL_FEEDBACK_APPLIED
    LUCKYBOX_PRODUCT_SOURCE_APPROVED
    LUCKYBOX_PRODUCT_RIGHTS_APPROVED
    LUCKYBOX_BRAND_COPY_APPROVED
    LUCKYBOX_INVOICE_POLICY_APPROVED
    LUCKYBOX_SHIPPING_OWNER_ASSIGNED
    LUCKYBOX_SHIPPING_OWNER
    LUCKYBOX_LOGISTICS_PROVIDER_APPROVED
    LUCKYBOX_LOGISTICS_PROVIDER
    LUCKYBOX_PRODUCTION_SMOKE_TEST_DONE
    LUCKYBOX_SMALL_TRAFFIC_TEST_DONE
    LUCKYBOX_LAUNCH_CHECKLIST_APPROVED
  )
  local name
  for name in "${names[@]}"; do
    printf '| `%s` | %s |\n' "$name" "$(display_value "$name")"
  done
}

emit_provider_rows() {
  local -a names=(
    LUCKYBOX_PAYMENT_ECPAY_ENABLED
    LUCKYBOX_PAYMENT_ECPAY_MERCHANT_ID
    LUCKYBOX_PAYMENT_ECPAY_HASH_KEY
    LUCKYBOX_PAYMENT_ECPAY_HASH_IV
    LUCKYBOX_PAYMENT_ECPAY_ACTION_URL
    LUCKYBOX_PAYMENT_ECPAY_RETURN_URL
    LUCKYBOX_PAYMENT_ECPAY_CLIENT_BACK_URL
    LUCKYBOX_PAYMENT_ECPAY_CREDIT_INSTALLMENT
    LUCKYBOX_PAYMENT_ECPAY_INSTALLMENT_CONTRACT_APPROVED
    LUCKYBOX_PAYMENT_LINEPAY_ENABLED
    LUCKYBOX_PAYMENT_LINEPAY_CHANNEL_ID
    LUCKYBOX_PAYMENT_LINEPAY_CHANNEL_SECRET
    LUCKYBOX_PAYMENT_LINEPAY_API_BASE_URL
    LUCKYBOX_PAYMENT_LINEPAY_CALLBACK_TESTED
    LUCKYBOX_PAYMENT_JKOPAY_ENABLED
    LUCKYBOX_PAYMENT_JKOPAY_API_KEY
    LUCKYBOX_PAYMENT_JKOPAY_SECRET_KEY
    LUCKYBOX_PAYMENT_JKOPAY_STORE_ID
    LUCKYBOX_PAYMENT_JKOPAY_ENTRY_URL
    LUCKYBOX_PAYMENT_JKOPAY_CONFIRM_URL
    LUCKYBOX_PAYMENT_JKOPAY_RESULT_URL
    LUCKYBOX_PAYMENT_JKOPAY_RESULT_DISPLAY_URL
    LUCKYBOX_PAYMENT_JKOPAY_CALLBACK_TESTED
    LUCKYBOX_PAYMENT_NEWEBPAY_MERCHANT_ID
    LUCKYBOX_PAYMENT_NEWEBPAY_HASH_KEY
    LUCKYBOX_PAYMENT_NEWEBPAY_HASH_IV
    LUCKYBOX_PAYMENT_NEWEBPAY_NOTIFY_URL
    LUCKYBOX_PAYMENT_NEWEBPAY_RETURN_URL
  )
  local name
  for name in "${names[@]}"; do
    printf '| `%s` | %s |\n' "$name" "$(display_value "$name")"
  done
}

emit_evidence_item() {
  local title="$1"
  local gates="$2"
  local evidence="$3"
  cat <<EOF
### $title

- Gates: $gates
- Required evidence: $evidence
- Owner:
- Approver:
- Approval date:
- Evidence link:
- Notes:

EOF
}

generate_template() {
  local generated_at
  generated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  cat <<EOF
# LuckyBox Launch Evidence

Generated at: $generated_at

Use this template with \`docs/launch-readiness.md\`,
\`docs/launch-signoff-register.md\`, and
\`scripts/check-launch-readiness.sh --env .env.production\`.

Do not paste real secrets into this file. Keep credentials in the deployment
secret store; attach only redacted screenshots, run output, or secure internal
links.

## Readiness Env Snapshot

| Gate | Current value |
| --- | --- |
EOF
  emit_gate_rows
  cat <<'EOF'

## Provider Env Snapshot

| Gate | Current value |
| --- | --- |
EOF
  emit_provider_rows
  cat <<'EOF'

## Business And Legal Evidence

EOF
  emit_evidence_item "Business registration confirmed" "\`LUCKYBOX_BUSINESS_REGISTRATION_APPROVED=true\`" "company name, tax ID, responsible owner, invoice/legal entity mapping"
  emit_evidence_item "Legal counsel assigned" "\`LUCKYBOX_LEGAL_COUNSEL_ASSIGNED=true\`" "reviewer name or firm, review date, scope, and open questions"
  emit_evidence_item "Legal review approved" "\`LUCKYBOX_LEGAL_REVIEW_APPROVED=true\`" "terms, privacy, refund, consumer-protection, minor-use, probability/fairness, and campaign disclosure review"
  emit_evidence_item "Legal feedback applied" "\`LUCKYBOX_LEGAL_FEEDBACK_APPLIED=true\`" "release note, commit, or issue link showing policy/document changes"
  cat <<'EOF'
## Product, Brand, And Copy Evidence

EOF
  emit_evidence_item "Product source approved" "\`LUCKYBOX_PRODUCT_SOURCE_APPROVED=true\`" "official distributor, parallel import, mixed-source, or custom bundle policy and required campaign labels"
  emit_evidence_item "Product rights approved" "\`LUCKYBOX_PRODUCT_RIGHTS_APPROVED=true\`" "supplier authorization, license terms, procurement record, or other business-approved rights proof"
  emit_evidence_item "Brand copy approved" "\`LUCKYBOX_BRAND_COPY_APPROVED=true\`" "legal/brand approval for SEO copy, trademark usage, and public campaign wording"
  emit_evidence_item "First official campaigns ready" "\`LUCKYBOX_FIRST_OFFICIAL_CAMPAIGNS_READY=true\`" "stock count, prize images, source labels, risk disclosures, shipping assumptions, and campaign owner approval"
  cat <<'EOF'
## Payment And Invoice Evidence

EOF
  emit_evidence_item "Payment provider contract approved" "\`LUCKYBOX_PAYMENT_PROVIDER_CONTRACT_APPROVED=true\`" "provider contract, production merchant id, callback URLs, staging/production callback test, and payment reconciliation output"
  emit_evidence_item "ECPay production ready" "\`LUCKYBOX_PAYMENT_PROVIDER=ECPAY\` and ECPay credential gates" "production merchant id, checkout action URL, ReturnURL, ClientBackURL, CheckMacValue callback test, and reconciliation output"
  emit_evidence_item "ECPay installment approved" "\`LUCKYBOX_PAYMENT_ECPAY_INSTALLMENT_CONTRACT_APPROVED=true\` when installments are enabled" "approved installment periods and successful sandbox or provider-dashboard payment result"
  emit_evidence_item "LINE Pay production ready" "\`LUCKYBOX_PAYMENT_PROVIDER=LINEPAY\`, LINE Pay credential gates, and \`LUCKYBOX_PAYMENT_LINEPAY_CALLBACK_TESTED=true\`" "channel id, API base URL, confirm/cancel callback URLs, provider dashboard callback test, and reconciliation output"
  emit_evidence_item "JKo Pay production ready" "\`LUCKYBOX_PAYMENT_PROVIDER=JKOPAY\`, JKo Pay credential gates, and \`LUCKYBOX_PAYMENT_JKOPAY_CALLBACK_TESTED=true\`" "store id, entry/confirm/result/result-display URLs, provider dashboard callback test, and reconciliation output"
  emit_evidence_item "Invoice policy approved" "\`LUCKYBOX_INVOICE_POLICY_APPROVED=true\`" "manual invoice process, e-invoice provider plan, or signed decision that invoice automation is out of launch scope"
  cat <<'EOF'
## Fulfillment Evidence

EOF
  emit_evidence_item "Shipping owner assigned" "\`LUCKYBOX_SHIPPING_OWNER_ASSIGNED=true\` and \`LUCKYBOX_SHIPPING_OWNER\`" "named owner/team and escalation contact"
  emit_evidence_item "Logistics provider approved" "\`LUCKYBOX_LOGISTICS_PROVIDER_APPROVED=true\` and \`LUCKYBOX_LOGISTICS_PROVIDER\`" "provider name, service level, tracking workflow, return-package process, and cost assumptions"
  emit_evidence_item "Convenience-store pickup policy approved" "\`LUCKYBOX_CONVENIENCE_STORE_PICKUP_POLICY_APPROVED=true\`" "enabled provider/process or signed launch decision not to support it"
  emit_evidence_item "International shipping policy approved" "\`LUCKYBOX_INTERNATIONAL_SHIPPING_POLICY_APPROVED=true\`" "supported countries, customs/tax owner, or signed launch decision not to support it"
  emit_evidence_item "Preorder campaign policy approved" "\`LUCKYBOX_PREORDER_POLICY_APPROVED=true\`" "whether preorder pools are allowed, ETA disclosures, delay handling, and refund/compensation policy"
  emit_evidence_item "SOPs approved" "\`LUCKYBOX_SUPPORT_SOP_APPROVED=true\`, \`LUCKYBOX_SHIPPING_SOP_APPROVED=true\`, \`LUCKYBOX_TAKEDOWN_SOP_APPROVED=true\`, \`LUCKYBOX_REFUND_SOP_APPROVED=true\`" "owner approvals for support, shipping, emergency takedown, and refund/compensation SOPs"
  cat <<'EOF'
## Deployment And Launch Evidence

EOF
  emit_evidence_item "Production deployment owner assigned" "\`LUCKYBOX_DEPLOYMENT_OWNER\`" "release owner, deployment window, deployment target, and runbook"
  emit_evidence_item "Production rollback owner assigned" "\`LUCKYBOX_ROLLBACK_OWNER\`" "rollback owner, rollback command/runbook, and rollback decision threshold"
  emit_evidence_item "Production smoke test passed" "\`LUCKYBOX_PRODUCTION_SMOKE_TEST_DONE=true\`" "\`scripts/smoke-test.sh https://production-origin\` output"
  emit_evidence_item "Backup restore drill completed" "\`LUCKYBOX_BACKUP_RESTORE_DRILL_DONE=true\`" "backup command output, restore target, restored DB/uploads evidence, and smoke-test output"
  emit_evidence_item "Small traffic test completed" "\`LUCKYBOX_SMALL_TRAFFIC_TEST_DONE=true\`" "monitored test window, order/payment/draw/shipment samples, incident log, and go/no-go decision"
  emit_evidence_item "Launch checklist approved" "\`LUCKYBOX_LAUNCH_CHECKLIST_APPROVED=true\`" "final approval note and launch announcement owner"
}

if [[ -n "$OUT_FILE" ]]; then
  mkdir -p "$(dirname "$OUT_FILE")"
  generate_template > "$OUT_FILE"
  printf 'Wrote launch evidence template: %s\n' "$OUT_FILE"
else
  generate_template
fi
