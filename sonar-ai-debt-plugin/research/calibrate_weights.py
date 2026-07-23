#!/usr/bin/env python3
"""Estimate exploratory CRITIC weights from paired Human/AI task outputs.

This script uses rank-normalized observations, so differently scaled raw metrics do not
dominate the result. Bootstrap resampling is clustered by task and keeps the Human/AI pair
together. It intentionally does not optimize separation between Human and AI: source type
is not a validated technical-debt outcome.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import random
from pathlib import Path
from statistics import fmean


TDSI_COLUMNS = {
    "AISD": ("Human_AISD", "AI_AISD", False),
    "CII": ("Human_CII", "AI_CII", False),
    "CDI": ("Human_CDI", "AI_CDI", False),
    "HTS": ("Human_HTS", "AI_HTS", True),
}
COGDI_COLUMNS = {
    "CSD": ("Human_CSD", "AI_CSD", False),
    "RLR": ("Human_RLR", "AI_RLR", False),
    "SII": ("Human_SII", "AI_SII", False),
    "EGR": ("Human_EGR", "AI_EGR", False),
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tdsi", type=Path, required=True, help="Paired TDSI CSV")
    parser.add_argument("--cogdi", type=Path, required=True, help="Paired CogDI CSV")
    parser.add_argument("--bootstrap", type=int, default=1000, help="Cluster bootstrap replicates")
    parser.add_argument("--seed", type=int, default=20260201)
    parser.add_argument("--output", type=Path, help="Optional JSON output path")
    return parser.parse_args()


def read_clusters(path: Path, columns: dict[str, tuple[str, str, bool]]) -> tuple[list[str], list[list[list[float]]]]:
    names = list(columns)
    clusters: list[list[list[float]]] = []
    with path.open(newline="", encoding="utf-8-sig") as handle:
        for row in csv.DictReader(handle):
            paired: list[list[float]] = [[], []]
            try:
                for human_column, ai_column, invert in columns.values():
                    human, ai = float(row[human_column]), float(row[ai_column])
                    if invert:
                        human, ai = 1.0 - human, 1.0 - ai
                    paired[0].append(human)
                    paired[1].append(ai)
            except (KeyError, TypeError, ValueError):
                continue
            if all(math.isfinite(value) for observation in paired for value in observation):
                clusters.append(paired)
    if len(clusters) < 20:
        raise ValueError(f"At least 20 complete paired tasks are required; found {len(clusters)} in {path}")
    return names, clusters


def average_ranks(values: list[float]) -> list[float]:
    order = sorted(range(len(values)), key=values.__getitem__)
    result = [0.0] * len(values)
    index = 0
    while index < len(order):
        end = index + 1
        while end < len(order) and values[order[end]] == values[order[index]]:
            end += 1
        rank = (index + end - 1) / 2.0
        scaled = rank / max(1, len(values) - 1)
        for position in order[index:end]:
            result[position] = scaled
        index = end
    return result


def transpose(matrix: list[list[float]]) -> list[list[float]]:
    return [list(column) for column in zip(*matrix)]


def correlation(left: list[float], right: list[float]) -> float:
    left_mean, right_mean = fmean(left), fmean(right)
    numerator = sum((x - left_mean) * (y - right_mean) for x, y in zip(left, right))
    left_ss = sum((x - left_mean) ** 2 for x in left)
    right_ss = sum((y - right_mean) ** 2 for y in right)
    if left_ss == 0 or right_ss == 0:
        return 0.0
    return numerator / math.sqrt(left_ss * right_ss)


def critic(matrix: list[list[float]]) -> list[float]:
    ranked = [average_ranks(column) for column in transpose(matrix)]
    information: list[float] = []
    for index, column in enumerate(ranked):
        mean = fmean(column)
        standard_deviation = math.sqrt(sum((value - mean) ** 2 for value in column) / max(1, len(column) - 1))
        conflict = sum(1.0 - abs(correlation(column, other)) for j, other in enumerate(ranked) if j != index)
        information.append(standard_deviation * conflict)
    total = sum(information)
    if total == 0:
        return [1.0 / len(information)] * len(information)
    return [value / total for value in information]


def flatten(clusters: list[list[list[float]]], indices: list[int] | None = None) -> list[list[float]]:
    chosen = range(len(clusters)) if indices is None else indices
    return [observation for index in chosen for observation in clusters[index]]


def percentile(values: list[float], probability: float) -> float:
    ordered = sorted(values)
    position = probability * (len(ordered) - 1)
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    return ordered[lower] * (upper - position) + ordered[upper] * (position - lower)


def estimate(names: list[str], clusters: list[list[list[float]]], replicates: int, rng: random.Random) -> dict:
    matrix = flatten(clusters)
    point = critic(matrix)
    columns = transpose(matrix)
    zero_variance = [name for name, column in zip(names, columns) if len(set(column)) <= 1]
    samples = [[] for _ in names]
    for _ in range(replicates):
        indices = [rng.randrange(len(clusters)) for _ in clusters]
        for index, value in enumerate(critic(flatten(clusters, indices))):
            samples[index].append(value)
    return {
        "paired_tasks": len(clusters),
        "observations": len(clusters) * 2,
        "method": "CRITIC on within-dataset average ranks; task-clustered percentile bootstrap",
        "zero_variance_metrics": zero_variance,
        "diagnostic": (
            "Zero-variance metrics cannot receive a data-driven CRITIC weight; investigate applicability and detector sensitivity."
            if zero_variance else "No zero-variance component was observed."
        ),
        "weights": {
            name: {
                "estimate": round(point[index], 6),
                "ci95": [round(percentile(samples[index], 0.025), 6), round(percentile(samples[index], 0.975), 6)],
            }
            for index, name in enumerate(names)
        },
    }


def main() -> None:
    args = parse_args()
    if args.bootstrap < 100:
        raise ValueError("Use at least 100 bootstrap replicates")
    rng = random.Random(args.seed)
    td_names, td_clusters = read_clusters(args.tdsi, TDSI_COLUMNS)
    cog_names, cog_clusters = read_clusters(args.cogdi, COGDI_COLUMNS)
    result = {
        "status": "exploratory_not_confirmatory",
        "warning": (
            "These weights measure empirical contrast and non-redundancy, not causal importance or criterion validity. "
            "Use equal weights for preregistered primary analysis unless expert ratings or an external debt outcome are available."
        ),
        "seed": args.seed,
        "bootstrap_replicates": args.bootstrap,
        "tdsi": estimate(td_names, td_clusters, args.bootstrap, rng),
        "cogdi": estimate(cog_names, cog_clusters, args.bootstrap, rng),
        "adsi": {"TDSI": 0.5, "CogDI": 0.5, "rationale": "Equal higher-order weights absent a validated outcome."},
    }
    text = json.dumps(result, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding="utf-8")
    print(text, end="")


if __name__ == "__main__":
    main()
