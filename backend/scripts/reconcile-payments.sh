#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_DB="$SCRIPT_DIR/../data/luckybox-dev.sqlite"
DB_PATH="$DEFAULT_DB"
STRICT=0

usage() {
  cat <<'EOF'
Usage: backend/scripts/reconcile-payments.sh [--strict] [--db PATH]
       backend/scripts/reconcile-payments.sh [--strict] PATH

Checks LuckyBox payment orders, webhook events, and wallet ledger rows for
common reconciliation issues.

Options:
  --db PATH   SQLite database path. Defaults to backend/data/luckybox-dev.sqlite.
  --strict    Exit with code 2 when reconciliation issues are found.
  -h, --help  Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --db)
      DB_PATH="${2:-}"
      shift 2
      ;;
    --strict)
      STRICT=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      DB_PATH="$1"
      shift
      ;;
  esac
done

if [[ -z "$DB_PATH" ]]; then
  echo "Missing database path." >&2
  usage >&2
  exit 1
fi

if ! command -v sqlite3 >/dev/null 2>&1; then
  echo "sqlite3 is required to run payment reconciliation." >&2
  exit 127
fi

if [[ ! -f "$DB_PATH" ]]; then
  echo "SQLite database not found: $DB_PATH" >&2
  exit 1
fi

issue_query() {
  cat <<'SQL'
WITH ledger AS (
  SELECT
    reference_id AS order_id,
    COALESCE(SUM(CASE WHEN type = 'TOP_UP' AND point_kind = 'CASH' THEN amount ELSE 0 END), 0) AS cash_top_up,
    COALESCE(SUM(CASE WHEN type = 'TOP_UP_BONUS' AND point_kind = 'BONUS' THEN amount ELSE 0 END), 0) AS bonus_top_up
  FROM wallet_ledger
  WHERE reference_type = 'PaymentOrder'
  GROUP BY reference_id
),
issues AS (
  SELECT
    'HIGH' AS severity,
    'PAID_LEDGER_MISMATCH' AS issue_code,
    p.id AS order_id,
    p.provider,
    p.merchant_trade_no,
    p.status AS order_status,
    NULL AS event_id,
    'expected cash=' || p.point_amount || ', actual cash=' || COALESCE(l.cash_top_up, 0)
      || '; expected bonus=' || p.bonus_point_amount || ', actual bonus=' || COALESCE(l.bonus_top_up, 0) AS message
  FROM payment_orders p
  LEFT JOIN ledger l ON l.order_id = p.id
  WHERE p.status = 'PAID'
    AND (
      COALESCE(l.cash_top_up, 0) != p.point_amount
      OR COALESCE(l.bonus_top_up, 0) != p.bonus_point_amount
    )

  UNION ALL

  SELECT
    'HIGH',
    'TERMINAL_ORDER_HAS_TOP_UP_LEDGER',
    p.id,
    p.provider,
    p.merchant_trade_no,
    p.status,
    NULL,
    'terminal order has cash=' || COALESCE(l.cash_top_up, 0) || ', bonus=' || COALESCE(l.bonus_top_up, 0)
  FROM payment_orders p
  LEFT JOIN ledger l ON l.order_id = p.id
  WHERE p.status IN ('FAILED', 'CANCELED')
    AND (COALESCE(l.cash_top_up, 0) > 0 OR COALESCE(l.bonus_top_up, 0) > 0)

  UNION ALL

  SELECT
    'HIGH',
    'PROCESSED_PAID_WEBHOOK_ORDER_NOT_PAID',
    p.id,
    p.provider,
    p.merchant_trade_no,
    p.status,
    e.event_id,
    'processed PAID webhook but order status is ' || p.status
  FROM payment_orders p
  JOIN payment_webhook_events e
    ON upper(e.provider) = upper(p.provider)
   AND e.merchant_trade_no = p.merchant_trade_no
  WHERE e.status = 'PAID'
    AND e.processed = 1
    AND p.status NOT IN ('PAID', 'REFUNDED')

  UNION ALL

  SELECT
    'MEDIUM',
    'PROCESSED_TERMINAL_WEBHOOK_STATUS_MISMATCH',
    p.id,
    p.provider,
    p.merchant_trade_no,
    p.status,
    e.event_id,
    'processed ' || e.status || ' webhook but order status is ' || p.status
  FROM payment_orders p
  JOIN payment_webhook_events e
    ON upper(e.provider) = upper(p.provider)
   AND e.merchant_trade_no = p.merchant_trade_no
  WHERE e.status IN ('FAILED', 'CANCELED')
    AND e.processed = 1
    AND p.status != e.status

  UNION ALL

  SELECT
    'HIGH',
    'WEBHOOK_AMOUNT_MISMATCH',
    p.id,
    p.provider,
    p.merchant_trade_no,
    p.status,
    e.event_id,
    'order amount=' || p.amount || ', webhook amount=' || e.amount || ', message=' || COALESCE(e.message, '')
  FROM payment_orders p
  JOIN payment_webhook_events e
    ON upper(e.provider) = upper(p.provider)
   AND e.merchant_trade_no = p.merchant_trade_no
  WHERE e.amount != p.amount
    OR e.message = 'AMOUNT_MISMATCH'

  UNION ALL

  SELECT
    'HIGH',
    'WEBHOOK_ORDER_NOT_FOUND',
    NULL,
    e.provider,
    e.merchant_trade_no,
    NULL,
    e.event_id,
    COALESCE(e.message, 'webhook has no matching payment order')
  FROM payment_webhook_events e
  LEFT JOIN payment_orders p
    ON upper(e.provider) = upper(p.provider)
   AND e.merchant_trade_no = p.merchant_trade_no
  WHERE p.id IS NULL
)
SELECT
  severity,
  issue_code,
  COALESCE(CAST(order_id AS TEXT), '') AS order_id,
  provider,
  merchant_trade_no,
  COALESCE(order_status, '') AS order_status,
  COALESCE(event_id, '') AS event_id,
  message
FROM issues
ORDER BY
  CASE severity WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END,
  issue_code,
  order_id,
  event_id
SQL
}

issue_count="$(
  {
    echo "SELECT COUNT(*) FROM ("
    issue_query
    echo ");"
  } | sqlite3 "$DB_PATH"
)"

echo "LuckyBox payment reconciliation"
echo "Database: $DB_PATH"
echo "Generated at: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo

sqlite3 "$DB_PATH" <<'SQL'
.headers on
.mode column
.print Payment orders by provider/status
SELECT provider, status, COUNT(*) AS order_count, COALESCE(SUM(amount), 0) AS total_amount
FROM payment_orders
GROUP BY provider, status
ORDER BY provider, status;

.print
.print Webhook events by provider/status/message
SELECT provider, status, processed, COALESCE(message, '') AS message, COUNT(*) AS event_count
FROM payment_webhook_events
GROUP BY provider, status, processed, message
ORDER BY provider, status, processed, message;
SQL

echo
echo "Reconciliation issues"
if [[ "$issue_count" == "0" ]]; then
  echo "No reconciliation issues found."
else
  {
    echo ".headers on"
    echo ".mode column"
    issue_query
    echo ";"
  } | sqlite3 "$DB_PATH"
fi
echo
echo "Issue count: $issue_count"

if [[ "$STRICT" == "1" && "$issue_count" != "0" ]]; then
  exit 2
fi
