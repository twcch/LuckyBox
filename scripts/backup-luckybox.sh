#!/usr/bin/env bash
set -euo pipefail

DB_PATH=""
UPLOAD_DIR="${LUCKYBOX_UPLOAD_DIR:-./uploads}"
OUT_DIR="${LUCKYBOX_BACKUP_DIR:-./backups}"

usage() {
  cat <<'EOF'
Usage: scripts/backup-luckybox.sh [--db PATH] [--uploads DIR] [--out DIR]

Creates a SQLite online backup, archives uploads when present, and writes a
manifest with SHA-256 checksums.

Options:
  --db PATH       SQLite database path. Defaults to LUCKYBOX_DB_URL when it is
                  a jdbc:sqlite URL, otherwise ./backend/data/luckybox-dev.sqlite.
  --uploads DIR  Uploaded asset directory. Defaults to LUCKYBOX_UPLOAD_DIR or ./uploads.
  --out DIR      Backup output directory. Defaults to LUCKYBOX_BACKUP_DIR or ./backups.
  -h, --help     Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --db)
      DB_PATH="${2:-}"
      shift 2
      ;;
    --uploads)
      UPLOAD_DIR="${2:-}"
      shift 2
      ;;
    --out)
      OUT_DIR="${2:-}"
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

if [[ -z "$DB_PATH" && "${LUCKYBOX_DB_URL:-}" == jdbc:sqlite:* ]]; then
  DB_PATH="${LUCKYBOX_DB_URL#jdbc:sqlite:}"
  DB_PATH="${DB_PATH%%\?*}"
fi

if [[ -z "$DB_PATH" ]]; then
  DB_PATH="./backend/data/luckybox-dev.sqlite"
fi

if ! command -v sqlite3 >/dev/null 2>&1; then
  echo "sqlite3 is required to back up LuckyBox." >&2
  exit 127
fi

if [[ ! -f "$DB_PATH" ]]; then
  echo "SQLite database not found: $DB_PATH" >&2
  exit 1
fi

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_dir="$OUT_DIR/$timestamp"
mkdir -p "$backup_dir"

db_backup="$backup_dir/luckybox.sqlite"
manifest="$backup_dir/MANIFEST.txt"

sqlite3 "$DB_PATH" ".backup '$db_backup'"

uploads_archive=""
if [[ -d "$UPLOAD_DIR" ]]; then
  uploads_archive="$backup_dir/uploads.tar.gz"
  tar -czf "$uploads_archive" -C "$(dirname "$UPLOAD_DIR")" "$(basename "$UPLOAD_DIR")"
fi

checksum() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1"
  else
    shasum -a 256 "$1"
  fi
}

{
  echo "LuckyBox backup manifest"
  echo "Generated at: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "Database source: $DB_PATH"
  echo "Uploads source: $UPLOAD_DIR"
  echo
  checksum "$db_backup"
  if [[ -n "$uploads_archive" ]]; then
    checksum "$uploads_archive"
  else
    echo "Uploads archive skipped: directory not found"
  fi
} > "$manifest"

echo "Backup created: $backup_dir"
echo "Manifest: $manifest"
