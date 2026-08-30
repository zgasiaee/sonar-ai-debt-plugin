# Metric specification

All composite inputs are debt-oriented in `[0, 1]`, where a larger value means more debt. Raw AISD and CDI are also published for auditability.

## Technical index

### AISD — AI-Specific Smell Density

`AISD_raw = unique SpecDetect4AI R1–R24 smell instances / KLOC`

The authoritative detector is SpecDetect4AI. Its native JSON report is ingested by the Sonar sensor, preserving file, line, rule identifier, and detector evidence. R11bis remains separately auditable but belongs to the R11 data-leakage family. Generic Python maintainability/security findings are not included in this numerator. If the report is absent, AISD is not applicable rather than zero. See [SpecDetect4AI integration](SPECDETECT4AI.md) and [AISD evidence and rule provenance](AISD_EVIDENCE.md). A finding is unique by file, line, and rule. The bounded value is:

`AISD = 1 − exp(−AISD_raw / 20)`

The scale parameter is a monotonic reference value, not an empirically validated clinical threshold. It must be included in sensitivity analysis.

### CII — Coupling Instability Index

For every coupled file `f`, `I_f = Ce_f / (Ca_f + Ce_f)`. Project CII is the arithmetic mean of applicable file values. Absolute and relative Python imports are resolved against module and `__init__.py` package paths. Internal imports contribute to both incoming and outgoing coupling; unresolved/external imports contribute to outgoing coupling and are reported separately. Files with no coupling are not applicable rather than being mislabeled perfectly stable.

### CDI — Complexity–Documentation Imbalance

`CDI = undocumented decision complexity / total decision complexity`

`decision complexity(block) = max(0, cyclomatic complexity(block) − 1)`

`comment density = comment lines / (logical code lines + comment lines)`

The current analysis unit is a Python callable block: a function, nested function, or method. Either an associated line comment or a function docstring is documentation evidence. Subtracting `1` removes cyclomatic complexity's mandatory straight-line baseline, so a straight-line callable does not create complexity-documentation debt. Each undocumented block contributes `(CC − 1) / total decision complexity`; the row contributions sum exactly to CDI. If the project has no decision complexity, CDI is zero. Comment density and callable documentation coverage remain separate supporting diagnostics and are not mixed into the CDI equation.

Cyclomatic complexity is computed from the Python AST for every function. It starts at `1` and increments for `if`, loops, exception handlers, comprehension clauses, and Boolean `and`/`or` decisions. Maximum control-flow nesting is published as a separate diagnostic; it is not added to CDI because nesting decisions have already contributed to cyclomatic complexity and including it again would double-count the same structure.

### HTS — Hyperparameter Transparency Score

The original HTS is a benefit indicator. To align direction with debt, the composite uses:

`HTS = 1 − (implicit ML initializations / all ML initializations)`

The analyzer resolves import aliases and recognizes common scikit-learn, XGBoost, LightGBM, CatBoost, Keras, and Transformers constructor/factory patterns. An initialization lacks statically explicit hyperparameters when it has neither direct arguments nor a resolvable `**configuration` expansion. Literal configuration dictionaries are traced to their keys; an expansion whose keys cannot be resolved is reported as opaque and remains on the non-transparent side of HTS. Missing `random_state`/seed parameters for stochastic components and missing Transformers `revision` pins are additional reproducibility findings only; they do not change the preregistered HTS numerator. If no supported ML initialization exists, HTS is not applicable.

### TDSI

`TDSI = weighted mean(AISD, CII, CDI, 1 − HTS)` over applicable components. The complement is used only to align aggregation direction; the published metric remains HTS.

## Cognitive index

### CSD — Context Switching Density

For CSD, *context* is the local style–structure pattern a reader builds while moving through consecutive code blocks. It has three independently normalized similarity channels:

- naming style: presence of casing conventions, function-name token shape, private prefixes, and Boolean-name prefixes across the function, parameters, and identifiers. Single-word lowercase and underscore-separated lowercase names both belong to Python's snake-case convention. Category presence is binary, so a larger function does not look stylistically different merely because it contains more or longer names;
- coding patterns: AST idioms such as loops, comprehensions, branching, exception handling, context managers, assignments, Boolean chains, calls, returns, raises, and awaits;
- structural shape: relative proximity of cyclomatic complexity, maximum nesting, source length, parameter count, and normalized-token count.

Each channel is in `[0, 1]`, preventing a raw count from dominating the model. Semantic vocabulary and operational responsibility (for example, local computation versus network access) are intentionally excluded: they describe what a function does, not how its code is styled. CSD therefore measures convention, idiom, and shape discontinuity. Callables are grouped by lexical scope (`<module>`, class, or enclosing function), ordered by source line, and only consecutive callables in the same scope form transitions. A class method is therefore not compared with a surrounding module-level function, and a scope with fewer than two callables contributes no transition. Adjacent same-scope callables below the configured similarity threshold are context switches:

`CSD = low-similarity adjacent transitions / all adjacent transitions`

The frozen consensus profile gives the three channels equal weight and uses a similarity
threshold of `0.45`. Configure them with `sonar.aidebt.csd.componentWeights` and
`sonar.aidebt.csd.similarityThreshold` only for an explicitly versioned sensitivity analysis;
the same values must be used for Human and AI code. The denominator count and
distribution summaries remain published as diagnostics, while the compact CSD artifact
stores only detected switches and their names, locations, overall/component similarities,
scope, and threshold.

### RLR — Redundant Logic Ratio

Function pairs are redundant when either normalized four-token shingle similarity is at least
`0.85`, or a combined deterministic behavioral-similarity proxy is at least `0.90`. The
proxy combines structural behavior, call-set similarity, and returned-identifier similarity;
the neutral default assigns equal weight to the three channels:

`RLR = redundant function pairs / analyzed function pairs`

This explicitly includes syntactic and lightweight behavioral redundancy without downloading
an embedding model during a scan. The behavioral value is a deterministic static proxy rather
than proof of runtime equivalence. RLR analyzes callable pairs; repeated variable names alone
are not counted because they do not establish duplicated behavior. Every detected pair retains
both callable source ranges and both similarity signals for dashboard auditing. The published
evidence is capped at 25 pairs for responsiveness, while the metric numerator retains every
detected pair within the configured analysis budget. The frozen boundaries are `0.85` for
syntax and `0.90` for behavioral similarity, with equal behavioral-channel weights.

### SII — Semantic Inconsistency Index

SII extracts function names, simple assigned-variable names, and class names with observable method context. Only identifiers of the same kind are compared. Identifier tokens are normalized to a small, versioned programming-concept vocabulary (for example, `calculate`/`compute` and `sum`/`total`). Each identifier also receives a deterministic numeric AST-context embedding: callable embeddings represent control flow, coding patterns, call families, complexity, nesting, and parameter shape; assigned-variable embeddings represent the AST shape and call/operator context of their assigned expressions; class embeddings aggregate their methods.

Three independent conditions are required. The normalized identifier concepts must be similar, the AST-context embeddings must be similar, and normalized Levenshtein similarity must be low. Callable pairs already classified as redundant by RLR are excluded from SII so the same duplicated implementation is not counted twice in CogDI. The frozen concept and context thresholds are `0.75`; the lexical ceiling is `0.40`:

`SII = high-concept/high-context/low-lexical same-kind identifier pairs / analyzed same-kind identifier pairs`

This hybrid guard prevents similarly shaped but conceptually different functions from being flagged and keeps callable-level redundancy in RLR. It is an offline, reproducible concept representation rather than a pretrained natural-language word embedding. Its coverage is therefore limited to the declared concept vocabulary and exact unmatched tokens; it does not prove synonymy or that either name is incorrect. Every detected pair retains both source ranges, all three similarities, and identifier kind. The evidence artifact is capped at 25 pairs while the numerator retains every detection within the configured analysis budget.

### EGR — Explanation Gap Ratio

A callable enters the complex/critical set when at least one auditable criterion holds: cyclomatic complexity reaches the frozen boundary `5`; maximum control-flow nesting reaches `3`; or complexity is at least `4` while at least two control-flow families (branching, iteration, error handling, async flow, or resource scope) are present. These values form the `consensus-adjudicated-v1` profile.

All EGR selection boundaries are configurable. “Multiple control-flow types” means that
the callable meets the combined criterion: it has at least the configured complexity and
contains at least the configured number of distinct control-flow families. A block may
legitimately satisfy more than one selection criterion; the evidence lists every criterion
it satisfies.

Associated comments and function docstrings are inspected. A rationale must contain a whole rationale term or phrase expressing cause, intent, invariant, safety, performance, fallback, retry, or validation language; an arbitrary descriptive comment is insufficient. External Markdown is not automatically credited because the analyzer cannot reliably establish which exact block it explains without an explicit traceable link.

`EGR = complex blocks without rationale / complex blocks`

The current-analysis evidence artifact records every selected block (up to the UI cap), its exact source range, selection triggers, complexity, nesting, control-flow families, and whether a comment or docstring supplied the rationale. The dedicated artifact prevents stale SonarQube issues from being mistaken for evidence from the latest scan.

### CogDI and ADSI

`CogDI = weighted mean(CSD, RLR, SII, EGR)` over applicable components.

`ADSI = α × TDSI + (1 − α) × CogDI`, with `α = 0.5` by default. If an entire higher-order index is unavailable, its weight is renormalized rather than imputed.

## Remediation-effort estimate

The normalized debt scores and remediation effort answer different questions and are not converted into one another. Scores describe relative debt severity; effort estimates the work needed to review or remediate the detected, countable actions. Following [SonarQube's technical-debt model](https://docs.sonarsource.com/sonarqube-server/user-guide/code-metrics/metrics-definition), effort is accumulated in minutes and one displayed day is eight hours.

The fixed policy follows [SonarSource's standard remediation durations](https://docs.sonarsource.com/sonarqube-server/extension-guide/adding-coding-rules) for languages other than ABAP and COBOL. Each unique repair action is classified as easy (`10 min`), medium (`20 min`), or major (`60 min`). These values are standardized remediation costs, not predictions of an individual developer's elapsed time.

| Signal | Countable remediation unit | Action policy |
|---|---|---|
| AISD | One unique source-located SpecDetect4AI occurrence | Easy, moderate, or major by rule family |
| CII | None until a concrete dependency violation and repair are defined | Not estimated |
| CDI | One undocumented callable block with decision complexity | Tiered by block complexity |
| HTS | One implicit or opaque ML initialization | Moderate or major |
| CSD | One flagged target callable in a style–structure transition | Easy |
| RLR | Minimum redundant implementations to consolidate per connected redundancy cluster | Major |
| SII | Minimum distinct names to harmonize per connected inconsistency cluster | Moderate |
| EGR | One selected complex block without rationale | Tiered by block complexity |

The tier is based on repair scope rather than debt severity: easy means one localized edit;
medium means several related local edits or configuration decisions; and major means a
multi-step behavioral, data-flow, or consolidation change. For SpecDetect4AI, R2, R6, R8,
R12–R16, R18, R20, R21, and R24 are easy; R1, R3, R5, R17, R19, and R23 are medium; and
R4, R7, R9–R11bis, and R22 are major. The policy deliberately does not use SonarSource's
high (`3 h`) or complex (`1 d`) categories because the current static evidence does not
establish project-wide repair scope strongly enough to justify those larger costs.

Metric-specific workload sums unique actions within that metric. TDSI, CogDI, and ADSI workload deduplicate shared repair keys before summing; for example, one rationale added to a block can address both CDI and EGR, and one explicit ML configuration can address both AISD R5 and HTS. The larger applicable tier is retained for a shared action. This prevents obvious double counting while preserving both metric signals.

The exact policy identifier, tier assignments, and deduplicated action counts are published in the `aidebt_effort_model` JSON measure, making the result reproducible across scans. Metric availability is separate: `100%` means every configured weighted metric had sufficient inputs and none was `N/A`; it does not mean perfect detection coverage or confidence.
