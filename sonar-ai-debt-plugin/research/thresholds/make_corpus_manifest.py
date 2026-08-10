#!/usr/bin/env python3
"""Reserve provenance-blind package groups and create a standalone-export manifest."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path


CALLABLE = re.compile(r"(?m)^(?:async\s+def|def|class)\s+")


def digest(prefix: str, value: str) -> str:
    return prefix + hashlib.sha256(value.encode()).hexdigest()[:12]


def selected(seed: str, group: str, fraction: float) -> bool:
    value = int.from_bytes(hashlib.sha256(f"{seed}|split|{group}".encode()).digest()[:8], "big")
    return value / 2**64 < fraction


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--arm", action="append", required=True,
                        help="Alternative implementation root; repeat for every blinded arm")
    parser.add_argument("--output", required=True)
    parser.add_argument("--reserve-fraction", type=float, default=0.20)
    parser.add_argument("--minimum-callables", type=int, default=2)
    parser.add_argument("--seed", default="20260727")
    args = parser.parse_args()
    if not 0 < args.reserve_fraction < 1:
        parser.error("--reserve-fraction must be in (0, 1)")

    output = Path(args.output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    rows: list[tuple[str, str, Path, str, str]] = []
    split_groups: dict[str, dict] = {}
    for arm_index, arm_value in enumerate(args.arm):
        arm = Path(arm_value).resolve()
        for path in sorted(arm.rglob("*.py")):
            if any(part in {".git", ".venv", "venv", "node_modules", "__pycache__"} for part in path.parts):
                continue
            try:
                content = path.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                continue
            if len(CALLABLE.findall(content)) < args.minimum_callables:
                continue
            relative = path.relative_to(arm)
            logical_group = str(relative.parent).replace("\\", "/")
            if logical_group in {"", "."}:
                logical_group = "root"
            group_id = digest("g-", f"{args.seed}|{logical_group}")
            if not selected(args.seed, logical_group, args.reserve_fraction):
                continue
            unit_id = digest("u-", f"{args.seed}|{arm_index}|{relative}")
            rows.append((group_id, unit_id, path, logical_group, str(relative)))
            split_groups.setdefault(group_id, {
                "logical_group": logical_group, "reserved_for_calibration": True, "units": []
            })["units"].append({"arm_index": arm_index, "relative_path": str(relative)})

    if not rows:
        raise SystemExit("No eligible Python files were selected; check roots and filters")
    manifest_lines = ["# group_id\tunit_id\tsource_path"]
    for group_id, unit_id, path, _, _ in rows:
        manifest_lines.append(f"{group_id}\t{unit_id}\t{path}")
    output.write_text("\n".join(manifest_lines) + "\n", encoding="utf-8")
    split_path = output.with_suffix(".split.json")
    split_path.write_text(json.dumps({
        "schema": "aidebt-calibration-split-v1",
        "seed": args.seed,
        "reserve_fraction": args.reserve_fraction,
        "minimum_callables": args.minimum_callables,
        "groups": split_groups,
    }, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Wrote {len(rows)} units in {len(split_groups)} reserved groups to {output}")
    print(f"Wrote the exclusion/audit manifest to {split_path}")


if __name__ == "__main__":
    main()
