#!/usr/bin/env bash
set -euo pipefail

ENV_FILE=""

usage() {
  cat <<'EOF'
Usage: scripts/check-launch-readiness.sh [--env FILE]

Checks whether the current environment has the required production launch
settings and sign-off flags. This script does not contact payment providers or
cloud vendors; it verifies the deployment contract before a release.

Options:
  --env FILE   Source an env file before checking.
  -h, --help   Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)
      ENV_FILE="${2:-}"
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

failures=0
warnings=0

value_of() {
  local name="$1"
  printf '%s' "${!name-}"
}

is_placeholder() {
  local value="$1"
  [[ -z "$value" || "$value" == REPLACE_* || "$value" == *"example.com"* || "$value" == "changeme" || "$value" == "CHANGE_ME" ]]
}

is_present() {
  local value="$1"
  ! is_placeholder "$value"
}

fail() {
  failures=$((failures + 1))
  printf 'FAIL  %s\n' "$1"
}

warn() {
  warnings=$((warnings + 1))
  printf 'WARN  %s\n' "$1"
}

pass() {
  printf 'PASS  %s\n' "$1"
}

require_value() {
  local name="$1"
  local label="$2"
  local value
  value="$(value_of "$name")"
  if is_placeholder "$value"; then
    fail "$label ($name) is missing or still a placeholder"
  else
    pass "$label ($name)"
  fi
}

require_true() {
  local name="$1"
  local label="$2"
  local value
  value="$(value_of "$name" | tr '[:upper:]' '[:lower:]')"
  if [[ "$value" == "true" || "$value" == "1" || "$value" == "yes" ]]; then
    pass "$label ($name)"
  else
    fail "$label ($name) must be true before launch"
  fi
}

require_https_url() {
  local name="$1"
  local label="$2"
  local value
  value="$(value_of "$name")"
  if is_placeholder "$value" || [[ "$value" != https://* ]]; then
    fail "$label ($name) must be a production HTTPS URL"
  elif [[ "$value" == *"localhost"* || "$value" == *"127.0.0.1"* ]]; then
    fail "$label ($name) must not point to localhost"
  else
    pass "$label ($name)"
  fi
}

require_not_dev_database() {
	local value
	value="$(value_of LUCKYBOX_DB_URL)"
  if is_placeholder "$value"; then
    fail "Production database URL (LUCKYBOX_DB_URL) is missing"
  elif [[ "$value" == *"luckybox-dev.sqlite"* ]]; then
    fail "Production database URL must not use luckybox-dev.sqlite"
  else
    pass "Production database URL (LUCKYBOX_DB_URL)"
	fi
}

require_named_owner() {
  local name="$1"
  local label="$2"
  require_value "$name" "$label"
}

check_optional_ecpay_installment() {
  local value
  value="$(value_of LUCKYBOX_PAYMENT_ECPAY_CREDIT_INSTALLMENT | tr -d '[:space:]' | tr '[:lower:]' '[:upper:]')"
  if ! is_present "$value"; then
    pass "ECPay credit installment disabled (LUCKYBOX_PAYMENT_ECPAY_CREDIT_INSTALLMENT)"
    return
  fi
  if [[ "$value" =~ ^(30N|((3|6|12|18|24)(,(3|6|12|18|24))*))$ ]]; then
    pass "ECPay credit installment periods (LUCKYBOX_PAYMENT_ECPAY_CREDIT_INSTALLMENT)"
  else
    fail "LUCKYBOX_PAYMENT_ECPAY_CREDIT_INSTALLMENT must be one or more of 3,6,12,18,24 or 30N"
  fi
  require_true LUCKYBOX_PAYMENT_ECPAY_INSTALLMENT_CONTRACT_APPROVED "ECPay installment contract approved"
}

echo "LuckyBox launch readiness"
echo "Generated at: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo

if [[ "$(value_of SPRING_PROFILES_ACTIVE)" == "prod" ]]; then
  pass "Spring production profile (SPRING_PROFILES_ACTIVE)"
else
  fail "SPRING_PROFILES_ACTIVE must be prod"
fi

if [[ "$(value_of LUCKYBOX_PAYMENT_MOCK_ENABLED | tr '[:upper:]' '[:lower:]')" == "false" ]]; then
  pass "Mock payment disabled (LUCKYBOX_PAYMENT_MOCK_ENABLED)"
else
  fail "LUCKYBOX_PAYMENT_MOCK_ENABLED must be false"
fi

require_not_dev_database
require_value LUCKYBOX_UPLOAD_DIR "Persistent uploads directory"
require_https_url LUCKYBOX_APP_BASE_URL "Public application base URL"
require_value LUCKYBOX_DOMAIN_NAME "Production domain"
require_value LUCKYBOX_BACKUP_DIR "Backup output directory"
require_value LUCKYBOX_UPTIME_MONITOR_URL "Uptime monitor URL"

provider="$(value_of LUCKYBOX_PAYMENT_PROVIDER | tr '[:lower:]' '[:upper:]')"
case "$provider" in
  ECPAY)
    pass "Payment provider selected (LUCKYBOX_PAYMENT_PROVIDER=ECPAY)"
    require_true LUCKYBOX_PAYMENT_ECPAY_ENABLED "ECPay integration enabled"
    require_value LUCKYBOX_PAYMENT_ECPAY_MERCHANT_ID "ECPay merchant id"
    require_value LUCKYBOX_PAYMENT_ECPAY_HASH_KEY "ECPay hash key"
    require_value LUCKYBOX_PAYMENT_ECPAY_HASH_IV "ECPay hash IV"
    require_https_url LUCKYBOX_PAYMENT_ECPAY_ACTION_URL "ECPay checkout action URL"
    require_https_url LUCKYBOX_PAYMENT_ECPAY_RETURN_URL "ECPay server return URL"
    require_https_url LUCKYBOX_PAYMENT_ECPAY_CLIENT_BACK_URL "ECPay client return URL"
    check_optional_ecpay_installment
    ;;
  NEWEBPAY)
    pass "Payment provider selected (LUCKYBOX_PAYMENT_PROVIDER=NEWEBPAY)"
    require_value LUCKYBOX_PAYMENT_NEWEBPAY_MERCHANT_ID "NewebPay merchant id"
    require_value LUCKYBOX_PAYMENT_NEWEBPAY_HASH_KEY "NewebPay hash key"
    require_value LUCKYBOX_PAYMENT_NEWEBPAY_HASH_IV "NewebPay hash IV"
    require_https_url LUCKYBOX_PAYMENT_NEWEBPAY_NOTIFY_URL "NewebPay notify URL"
    require_https_url LUCKYBOX_PAYMENT_NEWEBPAY_RETURN_URL "NewebPay return URL"
    ;;
  LINEPAY)
    pass "Payment provider selected (LUCKYBOX_PAYMENT_PROVIDER=LINEPAY)"
    require_true LUCKYBOX_PAYMENT_LINEPAY_ENABLED "LINE Pay integration enabled"
    require_value LUCKYBOX_PAYMENT_LINEPAY_CHANNEL_ID "LINE Pay channel id"
    require_value LUCKYBOX_PAYMENT_LINEPAY_CHANNEL_SECRET "LINE Pay channel secret"
    require_https_url LUCKYBOX_PAYMENT_LINEPAY_API_BASE_URL "LINE Pay API base URL"
    require_true LUCKYBOX_PAYMENT_LINEPAY_CALLBACK_TESTED "LINE Pay confirm callback tested in provider dashboard"
    ;;
  JKOPAY)
    pass "Payment provider selected (LUCKYBOX_PAYMENT_PROVIDER=JKOPAY)"
    require_true LUCKYBOX_PAYMENT_JKOPAY_ENABLED "JKo Pay integration enabled"
    require_value LUCKYBOX_PAYMENT_JKOPAY_API_KEY "JKo Pay API key"
    require_value LUCKYBOX_PAYMENT_JKOPAY_SECRET_KEY "JKo Pay secret key"
    require_value LUCKYBOX_PAYMENT_JKOPAY_STORE_ID "JKo Pay store id"
    require_https_url LUCKYBOX_PAYMENT_JKOPAY_ENTRY_URL "JKo Pay entry URL"
    require_https_url LUCKYBOX_PAYMENT_JKOPAY_CONFIRM_URL "JKo Pay confirm URL"
    require_https_url LUCKYBOX_PAYMENT_JKOPAY_RESULT_URL "JKo Pay result URL"
    require_https_url LUCKYBOX_PAYMENT_JKOPAY_RESULT_DISPLAY_URL "JKo Pay result display URL"
    require_true LUCKYBOX_PAYMENT_JKOPAY_CALLBACK_TESTED "JKo Pay callbacks tested in provider dashboard"
    ;;
  *)
    fail "LUCKYBOX_PAYMENT_PROVIDER must be ECPAY, NEWEBPAY, LINEPAY, or JKOPAY"
    ;;
esac

require_true LUCKYBOX_PAYMENT_PROVIDER_CONTRACT_APPROVED "Payment provider contract approved"
require_true LUCKYBOX_BUSINESS_REGISTRATION_APPROVED "Business registration confirmed"
require_true LUCKYBOX_LEGAL_COUNSEL_ASSIGNED "Legal counsel assigned"
require_true LUCKYBOX_LEGAL_REVIEW_APPROVED "Legal review approved"
require_true LUCKYBOX_LEGAL_FEEDBACK_APPLIED "Legal feedback applied to public documents"
require_true LUCKYBOX_PRODUCT_SOURCE_APPROVED "Product source model approved"
require_true LUCKYBOX_PRODUCT_RIGHTS_APPROVED "Product image/IP rights approved"
require_true LUCKYBOX_BRAND_COPY_APPROVED "Brand and marketing copy approved"
require_true LUCKYBOX_INVOICE_POLICY_APPROVED "Invoice policy approved"
require_true LUCKYBOX_PRODUCTION_DB_READY "Production database provisioned"
require_true LUCKYBOX_OBJECT_STORAGE_READY "Object storage or persistent uploads provisioned"
require_true LUCKYBOX_SSL_CONFIGURED "SSL configured"
require_true LUCKYBOX_CDN_CONFIGURED "CDN/cache configured or explicitly accepted"
require_true LUCKYBOX_SENTRY_CONFIGURED "Error reporting configured"
require_true LUCKYBOX_BACKUP_RESTORE_DRILL_DONE "Backup restore drill completed"
require_true LUCKYBOX_ADMIN_2FA_ENFORCED "Admin 2FA enforced"
require_true LUCKYBOX_FIRST_OFFICIAL_CAMPAIGNS_READY "First official campaigns ready"
require_true LUCKYBOX_SHIPPING_OWNER_ASSIGNED "Shipping owner assigned"
require_named_owner LUCKYBOX_SHIPPING_OWNER "Shipping owner"
require_true LUCKYBOX_LOGISTICS_PROVIDER_APPROVED "Logistics provider approved"
require_value LUCKYBOX_LOGISTICS_PROVIDER "Logistics provider"
require_true LUCKYBOX_CONVENIENCE_STORE_PICKUP_POLICY_APPROVED "Convenience-store pickup policy approved"
require_true LUCKYBOX_INTERNATIONAL_SHIPPING_POLICY_APPROVED "International shipping policy approved"
require_true LUCKYBOX_PREORDER_POLICY_APPROVED "Preorder campaign policy approved"
require_true LUCKYBOX_SUPPORT_SOP_APPROVED "Customer support SOP approved"
require_true LUCKYBOX_SHIPPING_SOP_APPROVED "Shipping SOP approved"
require_true LUCKYBOX_TAKEDOWN_SOP_APPROVED "Emergency takedown SOP approved"
require_true LUCKYBOX_REFUND_SOP_APPROVED "Refund/compensation SOP approved"
require_named_owner LUCKYBOX_DEPLOYMENT_OWNER "Production deployment owner"
require_named_owner LUCKYBOX_ROLLBACK_OWNER "Production rollback owner"
require_true LUCKYBOX_PRODUCTION_SMOKE_TEST_DONE "Production smoke test completed"
require_true LUCKYBOX_SMALL_TRAFFIC_TEST_DONE "Small traffic test completed"
require_true LUCKYBOX_LAUNCH_CHECKLIST_APPROVED "Final launch checklist approved"

if [[ -z "$(value_of LUCKYBOX_SENTRY_DSN)" || "$(value_of LUCKYBOX_SENTRY_DSN)" == REPLACE_* ]]; then
  warn "LUCKYBOX_SENTRY_DSN is not set; ensure your log aggregation path is documented if not using Sentry"
fi

echo
echo "Readiness summary: $failures failure(s), $warnings warning(s)"

if [[ "$failures" -gt 0 ]]; then
  exit 1
fi
