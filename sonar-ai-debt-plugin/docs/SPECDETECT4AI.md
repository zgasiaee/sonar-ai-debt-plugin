# SpecDetect4AI integration

AISD uses the native output of the supplied SpecDetect4AI implementation. The Java plugin does not reimplement or silently approximate its detectors.

## Rule catalogue

| ID | Smell |
|---|---|
| R1 | Broadcasting Feature Not Used |
| R2 | Random Seed Not Set |
| R3 | TensorArray Not Used |
| R4 | Training/Evaluation Mode Improper Toggling |
| R5 | Hyperparameter Not Explicitly Set |
| R6 | Deterministic Algorithm Option Not Used |
| R7 | Missing Mask of Invalid Value |
| R8 | PyTorch Call Method Misused |
| R9 | Gradients Not Cleared Before Backward Propagation |
| R10 | Memory Not Freed |
| R11 | Data Leakage: fit_transform Before Split |
| R11bis | Data Leakage: Model Fit Without Pipeline |
| R12 | Matrix Multiplication API Misused |
| R13 | Empty Column Misinitialization |
| R14 | DataFrame Conversion API Misused |
| R15 | Merge API Parameter Not Explicitly Set |
| R16 | API Result Discarded / Missing Reassignment or inplace |
| R17 | Unnecessary Iteration |
| R18 | NaN Comparison |
| R19 | Threshold Validation Metrics Count |
| R20 | Chained Indexing on DataFrames |
| R21 | Columns and Data Types Not Explicitly Set in DataFrame Read |
| R22 | No Scaling Before Scale-Sensitive Operation |
| R23 | EarlyStopping Not Used in Model.fit |
| R24 | Index Column Not Explicitly Set in DataFrame Read |

The upstream repository calls this a 24-smell catalogue. It exposes 25 executable identifiers because the R11 data-leakage family is split into R11 and R11bis. The integration preserves both identifiers instead of merging R11bis into R11.

## Data flow

1. SpecDetect4AI parses every Python file with Python's `ast` module and executes its generated R1–R24 matchers.
2. It writes findings grouped by file and rule to `specDetect4ai_results.json`.
3. The Sonar scanner reads the path configured by `sonar.aidebt.specdetect.reportPath`.
4. Each report item becomes source-located evidence and a Sonar external issue with a stable `SPECDETECT4AI-R…` identifier.
5. AISD counts unique `(file, line, rule)` instances and divides that count by analyzed KLOC.

Run the detector before the scanner:

```bash
python /path/to/SpecDetect4AI/specDetect4ai.py \
  --input-dir . --all --summary \
  --output-file specDetect4ai_results.json

sonar-scanner -Dsonar.token="$SONAR_TOKEN"
```

## Interpretation limits

The rules are static approximations. Category 1 rules mainly match local AST patterns; Category 2 rules infer missing configuration or lifecycle behavior; Category 3 rules approximate behavior that static analysis cannot prove fully. Therefore a finding is a review candidate, not proof of a runtime defect or proof that code was AI-generated. Precision, recall, and threshold sensitivity must still be validated for the thesis corpus.

The detector remains a third-party component and is not copied into the Apache-licensed
plugin JAR. The repository maintainer has confirmed its inclusion in the private
artifact. Before public archival, record the exact upstream license, immutable revision,
attribution, and citation alongside the retained snapshot.
