#!/usr/bin/env python3
"""Fit grouped, precision-constrained decision thresholds and freeze Sonar properties."""

from __future__ import annotations

import argparse
import itertools
import json
import random
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable

from calibration_common import METRICS, confusion, grid, parse_binary, quantile, read_jsonl, read_labels


PROPERTY_NAMES = {
    "CSD": {"similarity": "sonar.aidebt.csd.similarityThreshold"},
    "RLR": {
        "syntax": "sonar.aidebt.rlr.syntaxSimilarityThreshold",
        "behavior": "sonar.aidebt.rlr.behaviorSimilarityThreshold",
    },
    "SII": {
        "concept": "sonar.aidebt.sii.conceptSimilarityThreshold",
        "context": "sonar.aidebt.sii.contextSimilarityThreshold",
        "lexical": "sonar.aidebt.sii.nameSimilarityCeiling",
    },
    "EGR": {
        "cc": "sonar.aidebt.egr.complexityThreshold",
        "nesting": "sonar.aidebt.egr.nestingThreshold",
        "mixed_cc": "sonar.aidebt.egr.mixedFlowComplexityThreshold",
        "flow_kinds": "sonar.aidebt.egr.mixedFlowKindThreshold",
    },
}


def simplex(step: float = 0.25) -> list[tuple[float, float, float]]:
    units = round(1 / step)
    return [
        (first / units, second / units, (units - first - second) / units)
        for first in range(units + 1)
        for second in range(units - first + 1)
    ]


def configurations(metric: str, examples: list[tuple[dict[str, Any], int]]) -> Iterable[dict[str, float | int]]:
    rows = [row for row, _ in examples]
    if metric == "CSD":
        for naming, patterns, structure in simplex():
            similarities = [
                naming * row["naming_similarity"] + patterns * row["pattern_similarity"]
                + structure * row["structure_similarity"]
                for row in rows
            ]
            for value in grid(similarities, 21, (0.0, 1.0)):
                yield {
                    "similarity": value, "naming_weight": naming,
                    "pattern_weight": patterns, "structure_weight": structure,
                }
    elif metric == "RLR":
        syntax = grid((row["syntax_similarity"] for row in rows), 13, (0.0, 1.0))
        for context_weight, call_weight, output_weight in simplex():
            similarities = [
                context_weight * row["behavior_context_similarity"]
                + call_weight * row["call_similarity"]
                + output_weight * row["output_similarity"]
                for row in rows if row.get("behavior_available")
            ]
            behavior = grid(similarities, 13, (0.0, 1.0))
            for first, second in itertools.product(syntax, behavior):
                yield {
                    "syntax": first, "behavior": second,
                    "behavior_context_weight": context_weight,
                    "call_weight": call_weight, "output_weight": output_weight,
                }
    elif metric == "SII":
        concept = grid((row["concept_similarity"] for row in rows), 11, (0.0, 1.0))
        context = grid((row["context_similarity"] for row in rows), 11, (0.0, 1.0))
        lexical = grid((row["lexical_similarity"] for row in rows), 11, (0.0, 1.0))
        for first, second, third in itertools.product(concept, context, lexical):
            yield {"concept": first, "context": second, "lexical": third}
    else:
        maximum_cc = min(20, max(int(row["cyclomatic_complexity"]) for row in rows) + 1)
        maximum_nesting = min(10, max(int(row["maximum_nesting"]) for row in rows) + 1)
        maximum_flow = min(6, max(int(row["flow_family_count"]) for row in rows) + 1)
        for cc, nesting, mixed_cc, flow_kinds in itertools.product(
            range(1, maximum_cc + 1), range(1, maximum_nesting + 1),
            range(1, maximum_cc + 1), range(1, maximum_flow + 1)
        ):
            yield {"cc": cc, "nesting": nesting, "mixed_cc": mixed_cc, "flow_kinds": flow_kinds}


def predict(metric: str, row: dict[str, Any], threshold: dict[str, float | int]) -> bool:
    if metric == "CSD":
        similarity = (
            threshold["naming_weight"] * row["naming_similarity"]
            + threshold["pattern_weight"] * row["pattern_similarity"]
            + threshold["structure_weight"] * row["structure_similarity"]
        )
        return similarity < threshold["similarity"]
    if metric == "RLR":
        behavior = (
            threshold["behavior_context_weight"] * row["behavior_context_similarity"]
            + threshold["call_weight"] * row["call_similarity"]
            + threshold["output_weight"] * row["output_similarity"]
        )
        return row["syntax_similarity"] >= threshold["syntax"] or (
            row.get("behavior_available", False) and behavior >= threshold["behavior"]
        )
    if metric == "SII":
        return (
            not row.get("excluded_by_rlr", False)
            and row["concept_similarity"] >= threshold["concept"]
            and row["context_similarity"] >= threshold["context"]
            and row["lexical_similarity"] < threshold["lexical"]
        )
    return (
        row["cyclomatic_complexity"] >= threshold["cc"]
        or row["maximum_nesting"] >= threshold["nesting"]
        or (
            row["cyclomatic_complexity"] >= threshold["mixed_cc"]
            and row["flow_family_count"] >= threshold["flow_kinds"]
        )
    )


def fit(metric: str, examples: list[tuple[dict[str, Any], int]], minimum_precision: float) -> dict[str, Any]:
    labels = [label for _, label in examples]
    if len(set(labels)) < 2:
        raise ValueError(f"{metric}: both positive and negative labels are required")
    best: tuple[tuple[float, ...], dict[str, Any], dict[str, Any], bool] | None = None
    for threshold in configurations(metric, examples):
        performance = confusion(labels, [predict(metric, row, threshold) for row, _ in examples])
        feasible = performance["precision"] >= minimum_precision and performance["tp"] > 0
        if feasible:
            score = (
                1.0, performance["recall"], performance["f1"], performance["balanced_accuracy"],
                -performance["fp"],
            )
        else:
            score = (
                0.0, performance["f1"], performance["balanced_accuracy"], performance["precision"],
                -performance["fp"],
            )
        if best is None or score > best[0]:
            best = (score, threshold, performance, feasible)
    assert best is not None
    return {"thresholds": best[1], "performance": best[2], "precision_constraint_met": best[3]}


def degeneracy_reasons(metric: str, thresholds: dict[str, float | int]) -> list[str]:
    reasons: list[str] = []
    weight_names = {
        "CSD": ("naming_weight", "pattern_weight", "structure_weight"),
        "RLR": ("behavior_context_weight", "call_weight", "output_weight"),
    }.get(metric, ())
    if weight_names and max(float(thresholds[name]) for name in weight_names) >= 0.90:
        reasons.append("component weights collapsed onto one channel")
    if metric == "SII":
        for name in ("concept", "context", "lexical"):
            if float(thresholds[name]) <= 0.0 or float(thresholds[name]) >= 1.0:
                reasons.append(f"{name} boundary is at the edge of its search domain")
    if metric == "EGR":
        if int(thresholds["nesting"]) <= 1:
            reasons.append("nesting threshold selects ordinary single-level nesting")
        if int(thresholds["flow_kinds"]) <= 1:
            reasons.append("mixed-flow threshold does not require mixed flow")
    return reasons


def grouped_validation(metric: str, examples: list[tuple[dict[str, Any], int]],
                       minimum_precision: float) -> dict[str, Any]:
    groups: dict[str, list[tuple[dict[str, Any], int]]] = defaultdict(list)
    for example in examples:
        groups[str(example[0]["group_id"])].append(example)
    if len(groups) < 3:
        fitted = fit(metric, examples, minimum_precision)
        return {
            "method": "resubstitution-only",
            "warning": "At least three independent groups are required for leave-one-group-out validation.",
            "groups": len(groups),
            "performance": fitted["performance"],
        }
    labels: list[int] = []
    predictions: list[bool] = []
    folds: list[dict[str, Any]] = []
    for held_out in sorted(groups):
        training = [item for group, items in groups.items() if group != held_out for item in items]
        testing = groups[held_out]
        if len({label for _, label in training}) < 2:
            continue
        fitted = fit(metric, training, minimum_precision)
        fold_labels = [label for _, label in testing]
        fold_predictions = [predict(metric, row, fitted["thresholds"]) for row, _ in testing]
        labels.extend(fold_labels)
        predictions.extend(fold_predictions)
        folds.append({
            "held_out_group": held_out, "n": len(testing), "thresholds": fitted["thresholds"],
            "performance": confusion(fold_labels, fold_predictions),
        })
    if not folds:
        return {"method": "not-estimable", "groups": len(groups), "warning": "Training folds lacked both classes."}
    return {
        "method": "leave-one-group-out", "groups": len(groups), "folds": folds,
        "performance": confusion(labels, predictions),
    }


def bootstrap(metric: str, examples: list[tuple[dict[str, Any], int]], minimum_precision: float,
              repetitions: int, seed: int) -> dict[str, Any]:
    by_group: dict[str, list[tuple[dict[str, Any], int]]] = defaultdict(list)
    for example in examples:
        by_group[str(example[0]["group_id"])].append(example)
    groups = sorted(by_group)
    randomizer = random.Random(seed)
    estimates: dict[str, list[float]] = defaultdict(list)
    successful = 0
    for _ in range(repetitions):
        sampled_groups = [randomizer.choice(groups) for _ in groups]
        sample = [example for group in sampled_groups for example in by_group[group]]
        if len({label for _, label in sample}) < 2:
            continue
        fitted = fit(metric, sample, minimum_precision)
        successful += 1
        for name, value in fitted["thresholds"].items():
            estimates[name].append(float(value))
    intervals = {}
    for name, values in estimates.items():
        intervals[name] = {
            "median": quantile(values, 0.5),
            "lower_95": quantile(values, 0.025),
            "upper_95": quantile(values, 0.975),
        }
    return {"unit": "group", "requested": repetitions, "successful": successful, "threshold_intervals": intervals}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("exports", nargs="+")
    parser.add_argument("--labels", required=True)
    parser.add_argument("--report", required=True)
    parser.add_argument("--properties", required=True)
    parser.add_argument("--minimum-precision", type=float, default=0.80)
    parser.add_argument("--bootstrap", type=int, default=500)
    parser.add_argument("--seed", type=int, default=20260727)
    parser.add_argument("--allow-fallback-properties", action="store_true",
                        help="Write properties even when the minimum-precision constraint fails")
    args = parser.parse_args()
    if not 0 < args.minimum_precision <= 1:
        parser.error("--minimum-precision must be in (0, 1]")

    raw = read_jsonl(args.exports)
    labels = read_labels(args.labels)
    by_metric: dict[str, list[tuple[dict[str, Any], int]]] = defaultdict(list)
    rationale_examples: list[tuple[dict[str, Any], int]] = []
    missing = 0
    for row in raw:
        annotation = labels.get(row["candidate_id"])
        if annotation is None:
            missing += 1
            continue
        label = parse_binary(annotation.get("label", ""))
        if label is None:
            continue
        by_metric[row["metric"]].append((row, label))
        if row["metric"] == "EGR":
            rationale_label = parse_binary(annotation.get("rationale_label", ""))
            if rationale_label is not None:
                rationale_examples.append((row, rationale_label))

    report: dict[str, Any] = {
        "schema": "aidebt-threshold-fit-v1",
        "selection_objective": f"maximize recall subject to precision >= {args.minimum_precision:.3f}",
        "grouping": "project/task family; provenance-blind",
        "unlabeled_candidates": missing,
        "metrics": {},
    }
    properties: list[str] = [
        "# Frozen by research/thresholds/calibrate_thresholds.py",
        "# Do not tune these values on the Human-vs-AI outcome comparison.",
    ]
    for metric in METRICS:
        examples = by_metric.get(metric, [])
        if len(examples) < 4 or len({label for _, label in examples}) < 2:
            report["metrics"][metric] = {
                "status": "insufficient-labels", "labeled": len(examples),
                "class_counts": dict(Counter(label for _, label in examples)),
            }
            continue
        fitted = fit(metric, examples, args.minimum_precision)
        degeneracy = degeneracy_reasons(metric, fitted["thresholds"])
        result = {
            "status": "fitted", "labeled": len(examples),
            "class_counts": dict(Counter(label for _, label in examples)),
            **fitted,
            "degenerate_configuration": bool(degeneracy),
            "degeneracy_reasons": degeneracy,
            "grouped_validation": grouped_validation(metric, examples, args.minimum_precision),
            "bootstrap": bootstrap(metric, examples, args.minimum_precision, args.bootstrap, args.seed),
        }
        if metric == "EGR" and rationale_examples:
            result["rationale_detector"] = {
                "labeled": len(rationale_examples),
                "performance": confusion(
                    [label for _, label in rationale_examples],
                    [bool(row.get("has_rationale")) for row, _ in rationale_examples],
                ),
                "note": "This validates the keyword-based rationale classifier; it does not tune EGR selection thresholds.",
            }
        report["metrics"][metric] = result
        safe_to_freeze = fitted["precision_constraint_met"] and not degeneracy
        if safe_to_freeze or args.allow_fallback_properties:
            thresholds = fitted["thresholds"]
            if metric == "CSD":
                properties.append(
                    "sonar.aidebt.csd.componentWeights="
                    + ",".join(f"{thresholds[name]:.6f}" for name in (
                        "naming_weight", "pattern_weight", "structure_weight"
                    ))
                )
            if metric == "RLR":
                properties.append(
                    "sonar.aidebt.rlr.behaviorComponentWeights="
                    + ",".join(f"{thresholds[name]:.6f}" for name in (
                        "behavior_context_weight", "call_weight", "output_weight"
                    ))
                )
            for name, property_name in PROPERTY_NAMES[metric].items():
                value = thresholds[name]
                rendered = str(int(value)) if metric == "EGR" else f"{float(value):.6f}"
                properties.append(f"{property_name}={rendered}")
        else:
            reason = "minimum-precision constraint failed" if not fitted["precision_constraint_met"] \
                else "selected configuration is degenerate"
            properties.append(f"# {metric} omitted: {reason}")

    report_path = Path(args.report)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    properties_path = Path(args.properties)
    properties_path.parent.mkdir(parents=True, exist_ok=True)
    properties_path.write_text("\n".join(properties) + "\n", encoding="utf-8")
    print(f"Wrote calibration report to {report_path}")
    print(f"Wrote frozen Sonar properties to {properties_path}")


if __name__ == "__main__":
    main()
