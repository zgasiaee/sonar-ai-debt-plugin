# SpecDetect4AI Runtime Snapshot

This directory contains the authorized SpecDetect4AI snapshot used by the
Python-only Sonar AI Debt plugin to produce AISD evidence.

Only components required to execute, inspect, test, or regenerate the R1–R24
detectors are retained in this repository. Upstream evaluation datasets, RAG
experiments, and comparisons with unrelated tools are intentionally excluded
from the plugin artifact.

## Install

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
```

## Run

```bash
python specDetect4ai.py \
  --input-dir /path/to/python-project \
  --all \
  --summary \
  --output-file /path/to/python-project/specDetect4ai_results.json
```

The SonarQube scanner reads the resulting JSON file through
`sonar.aidebt.specdetect.reportPath`.

## Retained structure

```text
SpecDetect4AI-B903/
├── specDetect4ai.py
├── requirements.txt
├── grammar/
├── parser/
├── test_rules/
├── docs/
└── Dockerfile
```

- `test_rules/R*/generated_rules_R*.py` contains the executable detectors.
- `test_rules/R*/test_R*.dsl` contains the corresponding rule definitions.
- `test_rules/R*/test_R*.py` contains upstream regression tests.
- `parser/` and `grammar/` support regeneration from the DSL sources.
- `docs/Rules/` documents the retained smell catalogue.

This directory remains a third-party snapshot. Preserve its upstream license,
attribution, and citation requirements in public releases and derived archives.
