#!/usr/bin/env python3
"""Shared, dependency-free utilities for AI Debt threshold calibration."""

from __future__ import annotations

import csv
import json
import math
from pathlib import Path
from typing import Any, Iterable


METRICS = ("CSD", "RLR", "SII", "EGR")
LABEL_COLUMN = {
    "CSD": "label",
    "RLR": "label",
    "SII": "label",
    "EGR": "label",
}


def read_jsonl(paths: Iterable[str]) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    seen: set[str] = set()
    for value in paths:
        path = Path(value)
        candidates = sorted(path.rglob("*.jsonl")) if path.is_dir() else [path]
        for candidate in candidates:
            with candidate.open(encoding="utf-8") as handle:
                for line_number, line in enumerate(handle, 1):
                    if not line.strip():
                        continue
                    record = json.loads(line)
                    if record.get("record_type") != "candidate":
                        continue
                    identifier = str(record.get("candidate_id", ""))
                    if not identifier:
                        raise ValueError(f"{candidate}:{line_number}: missing candidate_id")
                    if identifier in seen:
                        raise ValueError(f"Duplicate candidate_id across exports: {identifier}")
                    seen.add(identifier)
                    records.append(record)
    if not records:
        raise ValueError("No calibration candidates were found")
    return records


def parse_binary(value: str) -> int | None:
    normalized = value.strip().lower()
    if normalized in {"1", "yes", "true", "positive", "present"}:
        return 1
    if normalized in {"0", "no", "false", "negative", "absent"}:
        return 0
    if normalized in {"", "u", "uncertain", "skip", "na", "n/a"}:
        return None
    raise ValueError(f"Expected 0, 1, or uncertain; received {value!r}")


def read_labels(path: str) -> dict[str, dict[str, str]]:
    result: dict[str, dict[str, str]] = {}
    with Path(path).open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.DictReader(handle)
        required = {"candidate_id", "label"}
        if not required.issubset(reader.fieldnames or []):
            raise ValueError(f"Label CSV must contain {sorted(required)}")
        for row_number, row in enumerate(reader, 2):
            identifier = (row.get("candidate_id") or "").strip()
            if not identifier:
                raise ValueError(f"{path}:{row_number}: blank candidate_id")
            if identifier in result:
                raise ValueError(f"{path}:{row_number}: duplicate candidate_id {identifier}")
            result[identifier] = row
    return result


def quantile(values: list[float], probability: float) -> float:
    if not values:
        return math.nan
    ordered = sorted(values)
    position = (len(ordered) - 1) * probability
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    fraction = position - lower
    return ordered[lower] * (1 - fraction) + ordered[upper] * fraction


def grid(values: Iterable[float], points: int = 21, include: tuple[float, ...] = ()) -> list[float]:
    finite = [float(value) for value in values if math.isfinite(float(value))]
    if not finite:
        return sorted(set(include))
    probabilities = [index / (points - 1) for index in range(points)] if points > 1 else [0.5]
    candidates = {round(quantile(finite, probability), 6) for probability in probabilities}
    candidates.update(round(value, 6) for value in include)
    return sorted(candidates)


def confusion(labels: list[int], predictions: list[bool]) -> dict[str, float | int]:
    tp = sum(label == 1 and prediction for label, prediction in zip(labels, predictions))
    fp = sum(label == 0 and prediction for label, prediction in zip(labels, predictions))
    tn = sum(label == 0 and not prediction for label, prediction in zip(labels, predictions))
    fn = sum(label == 1 and not prediction for label, prediction in zip(labels, predictions))
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    specificity = tn / (tn + fp) if tn + fp else 0.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    balanced = (recall + specificity) / 2
    return {
        "n": len(labels), "positives": sum(labels), "tp": tp, "fp": fp, "tn": tn, "fn": fn,
        "precision": precision, "recall": recall, "specificity": specificity,
        "f1": f1, "balanced_accuracy": balanced,
    }
