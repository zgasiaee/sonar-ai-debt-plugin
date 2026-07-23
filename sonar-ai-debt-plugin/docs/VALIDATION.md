# Validation protocol

The implementation makes metric calculation reproducible; it does not by itself establish that the metrics measure technical debt or that AI code has more debt.

## Required validation stages

1. **Rule-content validity:** at least three independent software-quality/ML reviewers label a stratified code sample. Report Fleiss' kappa or Krippendorff's alpha, adjudication rules, precision, recall, and uncertainty for each smell and rationale rule.
2. **Threshold calibration:** tune similarity and normalization parameters only on a training partition grouped by task/project. Freeze them before the confirmatory test.
   For CSD, have at least two reviewers label same-scope adjacent callable pairs as context continuity/switch, report inter-rater agreement, choose the threshold on the calibration partition (for example by balanced accuracy or an explicitly cost-weighted criterion), and report sensitivity around the selected value. Do not tune the threshold to maximize Human/AI separation.
3. **Criterion validity:** collect an outcome independent of code origin, such as blinded expert debt severity, remediation time, defect density, or maintenance-task effort. Do not train against the Human/AI label and then use the same index to claim a Human/AI difference.
4. **Construct validity:** test convergent/discriminant associations with established complexity, coupling, duplication, and maintainability measures. Check multicollinearity and measurement invariance across language, size, domain, and generation model.
5. **Reliability:** run repeated analyses, parser/version checks, inter-rater studies, and sensitivity analyses over thresholds and weights.
6. **Confirmatory comparison:** preserve task pairing. Use a paired permutation test or Wilcoxon signed-rank test, report the Hodges–Lehmann paired shift, rank-biserial effect size, bootstrap confidence intervals, and multiplicity correction for component analyses.

## Weighting policy

- **Primary/preregistered:** equal component weights and equal TDSI/CogDI weights. This is transparent and avoids fitting the conclusion.
- **Expert-derived alternative:** Delphi plus AHP; publish the comparison matrix, consistency ratio (`CR < 0.10`), panel composition, and uncertainty.
- **Outcome-derived alternative:** non-negative constrained regression against a blinded external debt outcome, with nested group cross-validation and coefficients normalized to sum to one.
- **Exploratory alternative:** the bundled CRITIC script weights metrics by contrast and non-redundancy after rank normalization. Its clustered bootstrap keeps Human/AI observations from the same task together.

Report all three reasonable specifications in a robustness table. A conclusion that changes sign under plausible weights or thresholds is not stable enough for a strong industrial recommendation.

## Sampling cautions

- Files within a project and Human/AI solutions for one task are not independent observations.
- Project size and language are potential confounders; match or adjust for them.
- Absence of an ML model makes HTS not applicable, not zero and not one.
- A source label is provenance, not ground-truth quality.

## Automated calculation checks

`GoldenMetricTest` contains hand-computable Python fixtures for package-aware CII, AST complexity and documentation evidence, resolved/opaque ML configuration, source-located smell rules, docstring rationale, and semantic/name inconsistency. These tests establish implementation correctness for the specified formulas; they do not replace the empirical construct-validation stages above.

For SII, label same-kind identifier pairs independently for contextual equivalence and naming inconsistency. Calibrate the context and Levenshtein thresholds on that labeled partition, report agreement between reviewers, and freeze both thresholds before the Human/AI comparison. Evaluate functions, assigned variables, and classes separately as well as together because their base rates differ.
