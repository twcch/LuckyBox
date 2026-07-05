#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/smoke-test.sh BASE_URL [API_BASE_URL]

Runs public production smoke checks. BASE_URL is the frontend/site origin.
API_BASE_URL defaults to BASE_URL/api.

Example:
  scripts/smoke-test.sh https://luckybox.example.com
  scripts/smoke-test.sh https://www.example.com https://api.example.com/api
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

BASE_URL="${1:-}"
API_BASE_URL="${2:-}"

if [[ -z "$BASE_URL" ]]; then
  usage >&2
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required to run smoke tests." >&2
  exit 127
fi

BASE_URL="${BASE_URL%/}"
if [[ -z "$API_BASE_URL" ]]; then
  API_BASE_URL="$BASE_URL/api"
else
  API_BASE_URL="${API_BASE_URL%/}"
fi

TIMEOUT="${SMOKE_TIMEOUT_SECONDS:-15}"
failures=0

check_get() {
  local url="$1"
  local label="$2"
  local status
  status="$(curl -k -sS -o /tmp/luckybox-smoke-response.$$ -w '%{http_code}' --max-time "$TIMEOUT" "$url" || true)"
  if [[ "$status" =~ ^[23] ]]; then
    printf 'PASS  %-34s %s\n' "$label" "$url"
  else
    failures=$((failures + 1))
    printf 'FAIL  %-34s %s (HTTP %s)\n' "$label" "$url" "${status:-curl-error}"
    if [[ -s /tmp/luckybox-smoke-response.$$ ]]; then
      head -c 400 /tmp/luckybox-smoke-response.$$
      printf '\n'
    fi
  fi
  rm -f /tmp/luckybox-smoke-response.$$
}

echo "LuckyBox smoke test"
echo "Site: $BASE_URL"
echo "API:  $API_BASE_URL"
echo "Generated at: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo

check_get "$BASE_URL/" "Frontend home"
check_get "$BASE_URL/terms" "Terms page"
check_get "$BASE_URL/privacy" "Privacy page"
check_get "$BASE_URL/shipping-policy" "Shipping policy page"
check_get "$API_BASE_URL/health" "API health"
check_get "$BASE_URL/actuator/health" "Actuator health"
check_get "$API_BASE_URL/campaigns" "Public campaigns"
check_get "$API_BASE_URL/news" "Public news"
check_get "$API_BASE_URL/banners" "Public banners"
check_get "$API_BASE_URL/leaderboard" "Public leaderboard"

echo
if [[ "$failures" -gt 0 ]]; then
  echo "Smoke test failed: $failures check(s) failed"
  exit 1
fi

echo "Smoke test passed"
