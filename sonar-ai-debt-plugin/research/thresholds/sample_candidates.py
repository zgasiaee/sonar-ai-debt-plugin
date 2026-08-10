#!/usr/bin/env python3
"""Create a score-blinded, group-balanced annotation sheet from raw candidates."""

from __future__ import annotations

import argparse
import csv
import hashlib
import random
from collections import defaultdict, deque
from pathlib import Path
from typing import Any

from calibration_common import METRICS, read_jsonl


FIELDS = [
    "candidate_id", "metric", "group_id", "review_item", "first_location", "second_location",
    "first_source", "second_source", "label", "rationale_label", "confidence", "reviewer_id", "notes",
]


def location(record: dict[str, Any], prefix: str = "") -> str:
    file_key = f"{prefix}file" if prefix else "file"
    line_key = f"{prefix}start_line" if prefix else "start_line"
    return f"{record.get(file_key, '')}:{record.get(line_key, '')}".strip(":")


def annotation_row(record: dict[str, Any]) -> dict[str, str]:
    metric = record["metric"]
    if metric == "CSD":
        item = f"Transition: {record['from_name']} -> {record['to_name']}"
        first_location = f"{record['file']}:{record['from_start_line']}"
        second_location = f"{record['file']}:{record['to_start_line']}"
        first_source, second_source = record.get("from_source", ""), record.get("to_source", "")
    elif metric in {"RLR", "SII"}:
        first_name = record.get("first_name", "")
        second_name = record.get("second_name", "")
        kind = f" ({record.get('kind')})" if metric == "SII" else ""
        item = f"Pair{kind}: {first_name} <-> {second_name}"
        first_location, second_location = location(record, "first_"), location(record, "second_")
        first_source, second_source = record.get("first_source", ""), record.get("second_source", "")
    else:
        item = f"Callable: {record.get('name', '')}"
        first_location, second_location = location(record), ""
        first_source, second_source = record.get("source", ""), ""
    return {
        "candidate_id": record["candidate_id"], "metric": metric, "group_id": record["group_id"],
        "review_item": item, "first_location": first_location, "second_location": second_location,
        "first_source": first_source, "second_source": second_source, "label": "",
        "rationale_label": "", "confidence": "", "reviewer_id": "", "notes": "",
    }


def uncertainty(record: dict[str, Any]) -> float:
    metric = record["metric"]
    if metric == "CSD":
        return abs(record["similarity"] - record["current_threshold"])
    if metric == "RLR":
        distances = [abs(record["syntax_similarity"] - record["current_syntax_threshold"])]
        if record.get("behavior_available"):
            distances.append(abs(record["behavior_similarity"] - record["current_behavior_threshold"]))
        return min(distances)
    if metric == "SII":
        return (
            abs(record["concept_similarity"] - record["current_concept_threshold"])
            + abs(record["context_similarity"] - record["current_context_threshold"])
            + abs(record["lexical_similarity"] - record["current_lexical_ceiling"])
        ) / 3
    return min(
        abs(record["cyclomatic_complexity"] - record["current_cc_threshold"]),
        abs(record["maximum_nesting"] - record["current_nesting_threshold"]),
    )


def stable_random(record: dict[str, Any], seed: int) -> float:
    digest = hashlib.sha256(f"{seed}|{record['candidate_id']}".encode()).digest()
    return int.from_bytes(digest[:8]) / 2**64


def select(records: list[dict[str, Any]], limit: int, seed: int) -> list[dict[str, Any]]:
    if len(records) <= limit:
        return sorted(records, key=lambda item: item["candidate_id"])
    boundary_count = limit // 2
    boundary = sorted(records, key=lambda item: (uncertainty(item), item["candidate_id"]))[:boundary_count]
    chosen = {item["candidate_id"] for item in boundary}
    remaining = [item for item in records if item["candidate_id"] not in chosen]
    strata: dict[tuple[str, bool], deque[dict[str, Any]]] = defaultdict(deque)
    for item in sorted(remaining, key=lambda row: stable_random(row, seed)):
        strata[(item["group_id"], bool(item.get("current_prediction")))].append(item)
    diverse: list[dict[str, Any]] = []
    keys = sorted(strata)
    while len(diverse) < limit - boundary_count and keys:
        next_keys = []
        for key in keys:
            if strata[key] and len(diverse) < limit - boundary_count:
                diverse.append(strata[key].popleft())
            if strata[key]:
                next_keys.append(key)
        keys = next_keys
    return boundary + diverse


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("exports", nargs="+", help="JSONL files or directories containing JSONL exports")
    parser.add_argument("--output", required=True)
    parser.add_argument("--per-metric", type=int, default=120)
    parser.add_argument("--seed", type=int, default=20260727)
    args = parser.parse_args()
    if args.per_metric < 1:
        parser.error("--per-metric must be positive")

    records = read_jsonl(args.exports)
    selected: list[dict[str, Any]] = []
    for metric in METRICS:
        selected.extend(select([row for row in records if row["metric"] == metric], args.per_metric, args.seed))
    random.Random(args.seed).shuffle(selected)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(annotation_row(record) for record in selected)
    counts = {metric: sum(row["metric"] == metric for row in selected) for metric in METRICS}
    print(f"Wrote {len(selected)} blinded candidates to {output}: {counts}")


if __name__ == "__main__":
    main()
