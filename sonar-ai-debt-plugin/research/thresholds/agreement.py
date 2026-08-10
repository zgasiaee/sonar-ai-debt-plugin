#!/usr/bin/env python3
"""Measure nominal Krippendorff alpha across independent annotation sheets."""

from __future__ import annotations

import argparse
import csv
from collections import Counter, defaultdict
from pathlib import Path

from calibration_common import METRICS, parse_binary


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("sheets", nargs="+", help="Independently completed annotation CSV files")
    args = parser.parse_args()
    ratings: dict[tuple[str, str], list[int]] = defaultdict(list)
    for sheet in args.sheets:
        with Path(sheet).open(newline="", encoding="utf-8-sig") as handle:
            for row in csv.DictReader(handle):
                label = parse_binary(row.get("label", ""))
                if label is not None:
                    ratings[(row["metric"], row["candidate_id"])].append(label)

    for metric in METRICS:
        items = [values for (name, _), values in ratings.items() if name == metric and len(values) >= 2]
        pair_disagreements = 0
        pair_total = 0
        marginal = Counter()
        for values in items:
            marginal.update(values)
            for first_index, first in enumerate(values):
                for second_index, second in enumerate(values):
                    if first_index == second_index:
                        continue
                    pair_total += 1
                    pair_disagreements += first != second
        observed = pair_disagreements / pair_total if pair_total else float("nan")
        ratings_total = sum(marginal.values())
        expected = 1.0 - sum((count / ratings_total) ** 2 for count in marginal.values()) if ratings_total else float("nan")
        alpha = 1.0 - observed / expected if expected and expected > 0 else float("nan")
        print(
            f"{metric}: overlap_items={len(items)}, ratings={ratings_total}, "
            f"observed_disagreement={observed:.4f}, nominal_alpha={alpha:.4f}"
        )


if __name__ == "__main__":
    main()
