# Validation protocol

The implementation makes metric calculation reproducible; it does not by itself establish that the metrics measure technical debt or that AI code has more debt.

## Required validation stages

1. **Rule-content validity:** at least three independent software-quality/ML reviewers label a stratified code sample. Report Fleiss' kappa or Krippendorff's alpha, adjudication rules, precision, recall, and uncertainty for each smell and rationale rule.
2. **Threshold calibration:** tune similarity and normalization parameters only on a
   calibration partition grouped by task/project. Freeze them before the confirmatory test.
   Use blinded labels about the construct itself, never Human/AI provenance, as ground
   truth. Select boundaries with a preregistered objective such as balanced accuracy or
   an explicitly cost-weighted false-positive/false-negative loss. Estimate uncertainty
   with project-grouped bootstrap resampling and report performance and sensitivity on a
   held-out project/task partition.

   - **CSD:** reviewers label adjacent same-scope callable pairs as continuity or
     style–structure switch.
   - **RLR:** reviewers label whether callable pairs are materially redundant. Tune the
     syntactic and behavioral boundaries jointly against the final OR rule.
   - **SII:** reviewers first label whether same-kind identifiers represent the same
     concept in equivalent usage contexts, then whether their vocabulary is inconsistent.
     Tune concept, context, and lexical boundaries jointly against the final conjunction.
   - **EGR:** reviewers label whether a callable is complex enough to require rationale
     and whether its associated documentation explains why. Evaluate candidate complexity,
     nesting, and combined control-flow boundaries against those labels.

   Report inter-rater agreement, adjudication rules, precision, recall, specificity,
   balanced accuracy, and confidence intervals. Do not optimize any threshold to maximize
   Human/AI separation.

   The executable export, blinded sampling, agreement, grouped fitting, group-bootstrap,
   and freeze workflow is specified in [`THRESHOLD_CALIBRATION.md`](THRESHOLD_CALIBRATION.md).
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

For pairwise metrics, record whether `sonar.aidebt.pairBudget` was reached. A capped run
is a partial estimate rather than an exhaustive project-wide pair ratio. Increase the
budget and rerun confirmatory analyses when resources permit, and report the configured
budget with scanner runtime. For SII, evaluate functions, assigned variables, and classes
separately as well as together because their base rates differ.
