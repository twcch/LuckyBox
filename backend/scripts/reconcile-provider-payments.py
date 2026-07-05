#!/usr/bin/env python3
import argparse
import csv
import sqlite3
import sys
from collections import Counter
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_DB = SCRIPT_DIR.parent / "data" / "luckybox-dev.sqlite"

COLUMN_ALIASES = {
    "merchant_trade_no": [
        "merchantTradeNo",
        "merchant_trade_no",
        "MerchantTradeNo",
        "MerchantTradeNO",
        "platform_order_id",
        "platformOrderId",
        "orderId",
        "order_id",
        "orderNo",
    ],
    "amount": [
        "amount",
        "Amount",
        "TotalAmount",
        "total_amount",
        "final_price",
        "finalPrice",
        "transactionAmount",
        "debit_amount",
    ],
    "status": [
        "status",
        "Status",
        "RtnCode",
        "rtn_code",
        "result",
        "resultCode",
        "transactionStatus",
        "paymentStatus",
    ],
    "event_id": [
        "eventId",
        "event_id",
        "TradeNo",
        "tradeNo",
        "transactionId",
        "transaction_id",
        "transactionSeq",
    ],
}


def usage_parser():
    parser = argparse.ArgumentParser(
        description=(
            "Compare a payment-provider CSV export against LuckyBox payment_orders."
        )
    )
    parser.add_argument("--db", default=str(DEFAULT_DB), help="SQLite database path.")
    parser.add_argument("--file", required=True, help="Provider CSV export path.")
    parser.add_argument("--provider", required=True, help="Provider name, e.g. ECPAY.")
    parser.add_argument("--merchant-trade-no-column", help="CSV column for LuckyBox merchant trade number.")
    parser.add_argument("--amount-column", help="CSV column for transaction amount.")
    parser.add_argument("--status-column", help="CSV column for provider status.")
    parser.add_argument("--event-id-column", help="Optional CSV column for provider event/transaction id.")
    parser.add_argument(
        "--paid-status",
        default="PAID,SUCCESS,SUCCESSFUL,COMPLETED,1",
        help="Comma-separated provider status values treated as paid.",
    )
    parser.add_argument(
        "--failed-status",
        default="FAILED,FAIL,FAILURE,ERROR,DECLINED",
        help="Comma-separated provider status values treated as failed.",
    )
    parser.add_argument(
        "--canceled-status",
        default="CANCELED,CANCELLED,CANCEL,VOIDED",
        help="Comma-separated provider status values treated as canceled.",
    )
    parser.add_argument(
        "--ignore-missing-local",
        action="store_true",
        help="Do not flag terminal LuckyBox orders that are absent from the provider file.",
    )
    parser.add_argument("--strict", action="store_true", help="Exit 2 when issues are found.")
    return parser


def key(value):
    return "".join(ch.lower() for ch in str(value or "") if ch.isalnum())


def status_set(value):
    return {key(part.strip()) for part in value.split(",") if part.strip()}


def pick_column(fieldnames, requested, alias_key, label):
    if not fieldnames:
        raise ValueError("CSV file has no header row.")
    by_key = {key(name): name for name in fieldnames}
    if requested:
        column = by_key.get(key(requested))
        if column:
            return column
        raise ValueError(f"CSV column for {label} not found: {requested}")
    for candidate in COLUMN_ALIASES[alias_key]:
        column = by_key.get(key(candidate))
        if column:
            return column
    raise ValueError(
        f"CSV column for {label} was not detected. Pass --{alias_key.replace('_', '-')}-column."
    )


def parse_amount(value):
    cleaned = str(value or "").strip().replace(",", "")
    if not cleaned:
        raise ValueError("missing amount")
    try:
        amount = Decimal(cleaned)
    except InvalidOperation as exception:
        raise ValueError(f"invalid amount: {value}") from exception
    if amount != amount.to_integral_value():
        raise ValueError(f"non-integer amount: {value}")
    return int(amount)


def map_status(value, paid_values, failed_values, canceled_values):
    normalized = key(value)
    if normalized in paid_values:
        return "PAID"
    if normalized in failed_values:
        return "FAILED"
    if normalized in canceled_values:
        return "CANCELED"
    return str(value or "").strip().upper()


def expected_local_status(local_status):
    status = str(local_status or "").upper()
    if status == "REFUNDED":
        return "PAID"
    return status


def read_provider_rows(args):
    csv_path = Path(args.file)
    if not csv_path.is_file():
        raise ValueError(f"Provider CSV not found: {csv_path}")

    paid_values = status_set(args.paid_status)
    failed_values = status_set(args.failed_status)
    canceled_values = status_set(args.canceled_status)

    with csv_path.open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.DictReader(handle)
        trade_column = pick_column(
            reader.fieldnames,
            args.merchant_trade_no_column,
            "merchant_trade_no",
            "merchant trade number",
        )
        amount_column = pick_column(reader.fieldnames, args.amount_column, "amount", "amount")
        status_column = pick_column(reader.fieldnames, args.status_column, "status", "status")
        event_column = None
        if args.event_id_column:
            event_column = pick_column(reader.fieldnames, args.event_id_column, "event_id", "event id")
        else:
            try:
                event_column = pick_column(reader.fieldnames, None, "event_id", "event id")
            except ValueError:
                event_column = None

        rows = []
        issues = []
        for line_number, row in enumerate(reader, start=2):
            merchant_trade_no = str(row.get(trade_column, "")).strip()
            raw_status = str(row.get(status_column, "")).strip()
            event_id = str(row.get(event_column, "")).strip() if event_column else ""
            if not merchant_trade_no and not any(str(value or "").strip() for value in row.values()):
                continue
            if not merchant_trade_no:
                issues.append(issue("HIGH", "PROVIDER_ROW_MISSING_MERCHANT_TRADE_NO", "", "", "", "", "", f"line {line_number} has no merchant trade number"))
                continue
            try:
                amount = parse_amount(row.get(amount_column))
            except ValueError as exception:
                issues.append(issue("HIGH", "PROVIDER_AMOUNT_INVALID", "", merchant_trade_no, "", raw_status, "", f"line {line_number}: {exception}"))
                continue
            rows.append(
                {
                    "line": line_number,
                    "merchant_trade_no": merchant_trade_no,
                    "amount": amount,
                    "status": map_status(raw_status, paid_values, failed_values, canceled_values),
                    "raw_status": raw_status,
                    "event_id": event_id,
                }
            )
    return rows, issues


def load_orders(database, provider):
    connection = sqlite3.connect(database)
    connection.row_factory = sqlite3.Row
    try:
        rows = connection.execute(
            """
            SELECT id, provider, merchant_trade_no, amount, status
            FROM payment_orders
            WHERE upper(provider) = upper(?)
            """,
            (provider,),
        ).fetchall()
        return {row["merchant_trade_no"]: dict(row) for row in rows}
    finally:
        connection.close()


def issue(severity, code, order_id, merchant_trade_no, local_status, provider_status, event_id, message):
    return {
        "severity": severity,
        "code": code,
        "order_id": str(order_id or ""),
        "merchant_trade_no": merchant_trade_no or "",
        "local_status": local_status or "",
        "provider_status": provider_status or "",
        "event_id": event_id or "",
        "message": message,
    }


def reconcile(args):
    database = Path(args.db)
    if not database.is_file():
        raise ValueError(f"SQLite database not found: {database}")

    provider_rows, issues = read_provider_rows(args)
    local_orders = load_orders(database, args.provider)
    row_counts = Counter(row["merchant_trade_no"] for row in provider_rows)
    provider_by_trade = {}

    for row in provider_rows:
        merchant_trade_no = row["merchant_trade_no"]
        if row_counts[merchant_trade_no] > 1 and merchant_trade_no not in provider_by_trade:
            issues.append(
                issue(
                    "MEDIUM",
                    "PROVIDER_DUPLICATE_ROW",
                    "",
                    merchant_trade_no,
                    "",
                    row["status"],
                    row["event_id"],
                    f"provider file contains {row_counts[merchant_trade_no]} rows for the same merchant trade number",
                )
            )
        provider_by_trade.setdefault(merchant_trade_no, row)

    for row in provider_rows:
        local = local_orders.get(row["merchant_trade_no"])
        if not local:
            issues.append(
                issue(
                    "HIGH",
                    "PROVIDER_ROW_ORDER_NOT_FOUND",
                    "",
                    row["merchant_trade_no"],
                    "",
                    row["status"],
                    row["event_id"],
                    "provider row has no matching LuckyBox payment order",
                )
            )
            continue
        if int(local["amount"]) != int(row["amount"]):
            issues.append(
                issue(
                    "HIGH",
                    "PROVIDER_AMOUNT_MISMATCH",
                    local["id"],
                    row["merchant_trade_no"],
                    local["status"],
                    row["status"],
                    row["event_id"],
                    f"local amount={local['amount']}, provider amount={row['amount']}",
                )
            )
        expected_status = expected_local_status(local["status"])
        if expected_status != row["status"]:
            issues.append(
                issue(
                    "HIGH",
                    "PROVIDER_STATUS_MISMATCH",
                    local["id"],
                    row["merchant_trade_no"],
                    local["status"],
                    row["status"],
                    row["event_id"],
                    f"local status={local['status']}, provider status={row['raw_status']}",
                )
            )

    if not args.ignore_missing_local:
        for local in local_orders.values():
            status = str(local["status"]).upper()
            if status not in {"PAID", "FAILED", "CANCELED", "REFUNDED"}:
                continue
            if local["merchant_trade_no"] not in provider_by_trade:
                issues.append(
                    issue(
                        "MEDIUM",
                        "PROVIDER_ORDER_MISSING_IN_FILE",
                        local["id"],
                        local["merchant_trade_no"],
                        local["status"],
                        "",
                        "",
                        "terminal LuckyBox payment order is absent from provider file",
                    )
                )
    return provider_rows, local_orders, issues


def print_report(args, provider_rows, local_orders, issues):
    print("LuckyBox provider payment reconciliation")
    print(f"Database: {args.db}")
    print(f"Provider: {args.provider.upper()}")
    print(f"Provider CSV: {args.file}")
    print(f"Generated at: {datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}")
    print()
    print(f"Provider rows read: {len(provider_rows)}")
    print(f"Local provider orders: {len(local_orders)}")
    matched = sum(1 for row in provider_rows if row["merchant_trade_no"] in local_orders)
    print(f"Matched provider rows: {matched}")
    print()
    print("Provider reconciliation issues")
    if not issues:
        print("No provider reconciliation issues found.")
    else:
        headers = [
            "severity",
            "issue_code",
            "order_id",
            "merchant_trade_no",
            "local_status",
            "provider_status",
            "event_id",
            "message",
        ]
        print("\t".join(headers))
        severity_order = {"HIGH": 1, "MEDIUM": 2, "LOW": 3}
        for item in sorted(issues, key=lambda value: (severity_order.get(value["severity"], 9), value["code"], value["merchant_trade_no"])):
            print(
                "\t".join(
                    [
                        item["severity"],
                        item["code"],
                        item["order_id"],
                        item["merchant_trade_no"],
                        item["local_status"],
                        item["provider_status"],
                        item["event_id"],
                        item["message"],
                    ]
                )
            )
    print()
    print(f"Issue count: {len(issues)}")


def main():
    parser = usage_parser()
    args = parser.parse_args()
    try:
        provider_rows, local_orders, issues = reconcile(args)
    except (sqlite3.Error, ValueError) as exception:
        print(str(exception), file=sys.stderr)
        return 1
    print_report(args, provider_rows, local_orders, issues)
    if args.strict and issues:
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
