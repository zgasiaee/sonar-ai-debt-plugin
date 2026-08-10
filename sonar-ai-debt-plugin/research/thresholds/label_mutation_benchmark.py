#!/usr/bin/env python3
"""Assign construction-based oracle labels to the generated mutation benchmark."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path

from calibration_common import read_jsonl


def label(record: dict) -> tuple[int | None, str]:
    metric = record["metric"]
    if metric == "CSD":
        if not record["from_name"].startswith(("csd_", "csdSwitch")):
            return None, ""
        if not record["to_name"].startswith(("csd_", "csdSwitch")):
            return None, ""
        positive = "csdSwitch" in record["from_name"] or "csdSwitch" in record["to_name"]
        return int(positive), ""
    if metric == "RLR":
        if not record["first_name"].startswith("rlr_") or not record["second_name"].startswith("rlr_"):
            return None, ""
        positive = record["first_name"].startswith("rlr_clone_") and record["second_name"].startswith("rlr_clone_")
        return int(positive), ""
    if metric == "SII":
        if not record["first_scope"].startswith("sii_") or not record["second_scope"].startswith("sii_"):
            return None, ""
        names = {record["first_name"], record["second_name"]}
        concepts = (
            any(name.startswith("sii_total_") for name in names)
            and any(name.startswith("sii_aggregate_") for name in names)
        ) or names == {"total", "aggregate"}
        return int(concepts), ""
    if not record["name"].startswith("egr_"):
        return None, ""
    complex_required = record["name"].startswith("egr_complex_")
    has_rationale = record["name"].startswith("egr_complex_explained_")
    return int(complex_required), str(int(has_rationale))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("exports", nargs="+")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    rows = []
    for record in read_jsonl(args.exports):
        oracle, rationale = label(record)
        if oracle is None:
            continue
        rows.append({
            "candidate_id": record["candidate_id"], "metric": record["metric"],
            "label": oracle, "rationale_label": rationale, "confidence": "construction",
            "reviewer_id": "mutation-oracle", "notes": "",
        })
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {len(rows)} construction-based labels to {output}")


if __name__ == "__main__":
    main()
