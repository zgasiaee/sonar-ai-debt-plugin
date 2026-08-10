#!/usr/bin/env python3
"""Generate provenance-neutral mutation cases with construction-based labels."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path


TEMPLATE = """\
def csd_stable_{index}_a(items):
    total = 0
    for item in items:
        if item > 0:
            total += item
    return total

def csd_stable_{index}_b(values):
    result = 0
    for value in values:
        if value > 0:
            result += value
    return result

def csdSwitch{index}(payload, client, cache):
    try:
        response = client.fetch(payload)
        cache[payload] = response
        return response
    except TimeoutError:
        return None

def rlr_clone_{index}_a(items):
    total = 0
    for item in items:
        if item > 0:
            total += item
    return total

def rlr_clone_{index}_b(values):
    result = sum(value for value in values if value > 0)
    return result

def rlr_distinct_{index}(mapping, key):
    if key not in mapping:
        mapping[key] = []
    return mapping[key]

def sii_total_{index}(items):
    total = sum(items)
    return total

def sii_aggregate_{index}(values):
    aggregate = sum(values)
    return aggregate

def sii_count_{index}(values):
    count = len(values)
    return count

def egr_simple_{index}(value):
    return value if value is not None else 0

def egr_complex_gap_{index}(records, enabled, limit):
    result = []
    if enabled:
        for record in records:
            if record is None:
                continue
            if record > limit:
                try:
                    result.append(record)
                except ValueError:
                    break
    return result

def egr_complex_explained_{index}(records, enabled, limit):
    # We skip invalid entries because downstream consumers require validated positive values.
    result = []
    if enabled:
        for record in records:
            if record is None:
                continue
            if record > limit:
                result.append(record)
    return result
"""


def opaque(prefix: str, value: str) -> str:
    return prefix + hashlib.sha256(value.encode()).hexdigest()[:10]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    parser.add_argument("--groups", type=int, default=12)
    parser.add_argument("--seed", default="20260727")
    args = parser.parse_args()
    root = Path(args.output).resolve()
    sources = root / "sources"
    sources.mkdir(parents=True, exist_ok=True)
    manifest = root / "manifest.tsv"
    lines = ["# group_id\tunit_id\tsource_path"]
    for index in range(args.groups):
        group = opaque("g-", f"{args.seed}|group|{index}")
        unit = opaque("u-", f"{args.seed}|unit|{index}")
        source = sources / f"case_{index:03d}.py"
        source.write_text(TEMPLATE.format(index=index), encoding="utf-8")
        lines.append(f"{group}\t{unit}\t{source}")
    manifest.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {args.groups} mutation groups and {manifest}")


if __name__ == "__main__":
    main()
