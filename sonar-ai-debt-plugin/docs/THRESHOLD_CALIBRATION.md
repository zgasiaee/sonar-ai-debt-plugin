# Threshold Calibration Protocol

## Purpose

The CSD, RLR, SII, and EGR decision boundaries are model parameters, not universal
constants. The values shipped with the plug-in belong to the frozen
`consensus-adjudicated-v1` profile. The profile combines provenance-blind annotations,
codebook adjudication, grouped performance checks, and conservative retention of the
pre-specified boundary when the labeled sample does not identify a safer replacement.

The calibration outcome must not use the source-code provenance label (Human or AI).
Otherwise, the analyzer is indirectly optimized to create the difference that the study is
supposed to test.

## Evidence strategy

The recommended design minimizes expert effort without claiming that unlabeled data can
identify a semantic construct by itself:

1. **Mutation and benchmark oracle.** Create known positive and negative transformations
   for style discontinuity, redundant behavior, naming inconsistency, and complex blocks.
   These cases provide reproducible coverage and recall evidence.
2. **Independent real-project evidence.** Export every pre-threshold candidate from projects
   that are not in the final Human-versus-AI comparison.
3. **Blinded active sample.** Label boundary cases and a group-balanced random sample. The
   annotation sheet hides feature scores, current thresholds, current predictions, and
   provenance.
4. **Small overlap audit.** A second reviewer labels only an overlapping subset. Report
   nominal Krippendorff alpha and adjudicate disagreements.
5. **Grouped validation.** Select thresholds on whole-project/task training groups and
   validate on held-out groups. Never split candidate pairs randomly across train and test.
6. **Freeze.** Commit the generated properties file and its calibration report before the
   confirmatory comparison.

Synthetic cases and weak supervision can substantially reduce manual work, but they cannot
establish real-project precision alone. A small blinded precision audit remains the most
defensible minimum.

The repository includes a construction-based smoke benchmark:

```bash
python3 research/thresholds/generate_mutation_benchmark.py \
  --output research/output/mutation-benchmark
```

Export its manifest with the standalone CLI described below, then create oracle labels with
`label_mutation_benchmark.py`. This benchmark verifies threshold direction, joint-rule
search, and known transformations. Its repeated templates are deliberately not sufficient
for freezing production thresholds; perfect performance on them is a unit/recall check, not
external validity.

## 1. Export all candidates

Add these properties to the project being scanned:

```properties
sonar.aidebt.calibration.exportPath=.aidebt-calibration/project-a.jsonl
sonar.aidebt.calibration.groupId=project-family-a
```

`groupId` is the independent unit used during validation. Use a repository, project, or task
family. Do not put `human`, `ai`, a model name, or any other provenance clue in it.

The JSONL export includes candidates that do and do not cross the current threshold:

- CSD: every consecutive same-scope callable transition and its three similarity components;
- RLR: every analyzed callable pair and its syntax and behavioral scores;
- SII: every same-kind identifier pair, including callable pairs excluded by RLR;
- EGR: every callable, its complexity/nesting/control-flow inputs, and rationale status.

For pairwise metrics, set `sonar.aidebt.pairBudget` above the largest candidate count or
explicitly report any truncation. A truncated export cannot estimate corpus-wide recall.

### Standalone corpus export

For many independent units, create a tab-separated manifest with
`group_id`, `unit_id`, and `source_path`, then run:

```bash
mvn -q compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=io.github.aidebt.research.CalibrationExportCli \
  -Dexec.args="--manifest calibration/manifest.tsv --output calibration/candidates.jsonl"
```

`group_id` controls validation separation. `unit_id` distinguishes alternative solutions
inside a group and is included in the candidate hash, but neither identifier should reveal
source provenance.

The helper below reserves complete package groups from alternative corpus roots, creates
opaque IDs, and writes an audit/exclusion manifest:

```bash
python3 research/thresholds/make_corpus_manifest.py \
  --arm /corpus/alternative-a \
  --arm /corpus/alternative-b \
  --output calibration/manifest.tsv \
  --reserve-fraction 0.20 \
  --minimum-callables 2 \
  --seed 20260727
```

Every reserved group must be excluded from the later confirmatory Human-versus-AI test.

## 2. Create a blinded annotation sheet

```bash
python3 research/thresholds/sample_candidates.py \
  calibration-exports/ \
  --output calibration/annotations-r1.csv \
  --per-metric 120 \
  --seed 20260727
```

Half of each metric sample is concentrated near the current decision boundary; the other
half is balanced across project groups and current prediction classes. Numeric feature
scores are retained only in the raw JSONL and are not shown to reviewers.

Allowed labels are `1`, `0`, and `uncertain`. Uncertain cases are excluded from fitting but
must be reported.

### CSD codebook

Label `1` only when two consecutive, same-scope callables create an unexpected
style/structure discontinuity that forces the reader to rebuild the local coding model.
Consider naming convention, coding idioms, and structural shape. A change in business
responsibility or domain alone is not a context switch. Label `0` when the transition follows
a coherent local style even if the functions perform different tasks.

### RLR codebook

Label `1` when two callables implement materially the same responsibility and one could be
reused, parameterized, or consolidated without changing intended behavior. Purely shared
syntax, common boilerplate, or two functions with different contracts are negative. Inspect
both callables as a pair.

### SII codebook

Label `1` when identifiers denote the same project concept in comparable roles/contexts but
use unnecessarily divergent names. Different names for genuinely different concepts are
negative. If two functions are themselves redundant implementations, label the case for RLR;
the operational SII classifier excludes that overlap.

### EGR codebook

The main `label` answers: “Does this callable require a rationale to remain understandable
and maintainable?” Use cyclomatic branching, nesting, and interacting control-flow families,
not size alone. The optional `rationale_label` answers whether the shown documentation
actually explains *why* a decision exists. Descriptive comments that merely restate the code
are not rationale.

Record reviewer confidence (`high`, `medium`, or `low`) and a short note for uncertain or
borderline cases.

## 3. Check the overlap audit

Give independently shuffled copies of the same overlap subset to two reviewers, then run:

```bash
python3 research/thresholds/agreement.py \
  calibration/annotations-r1.csv \
  calibration/annotations-r2.csv
```

Report agreement per metric rather than one pooled number. Low agreement indicates that the
construct definition or examples need revision; changing the numeric threshold cannot repair
an ambiguous construct.

## 4. Fit and validate thresholds

After adjudication, retain one row per candidate in a CSV and run:

```bash
python3 research/thresholds/calibrate_thresholds.py \
  calibration-exports/ \
  --labels calibration/adjudicated.csv \
  --report calibration/threshold-report.json \
  --properties calibration/frozen-thresholds.properties \
  --minimum-precision 0.80 \
  --bootstrap 500 \
  --seed 20260727
```

The fitting rule maximizes recall subject to the preregistered minimum precision. If no
configuration satisfies the precision constraint, the report marks that failure and selects
the best fallback by F1/balanced accuracy; it must not be presented as a validated boundary.

The report contains:

- final fit and confusion matrix;
- leave-one-group-out performance when at least three independent groups exist;
- group-bootstrap 95% intervals for every threshold;
- class counts, sample sizes, and warnings;
- whether the minimum-precision constraint was satisfied.

CSD channel weights and its boundary are tuned jointly. RLR behavioral-channel weights and
both redundancy boundaries are tuned jointly. SII boundaries and EGR selection inputs are
also tuned jointly because their rules combine several conditions. Tuning each component
in isolation would not optimize the behavior of the published rule.

## 5. Freeze before confirmatory analysis

Append the generated `frozen-thresholds.properties` to the scanner properties used for both
Human and AI code. Archive:

- plug-in commit and JAR checksum;
- raw JSONL exports;
- codebook version;
- blinded and adjudicated annotation sheets;
- random seed;
- threshold report and frozen properties;
- exact project/task group assignment.

Do not change thresholds after inspecting Human-versus-AI results. Any later modification is
an exploratory analysis and must be reported separately.

## What counts as a defensible result?

A point estimate is not enough. A threshold is ready for confirmatory reporting only when:

- both classes are represented for that metric;
- held-out groups are available and performance is acceptable;
- bootstrap intervals are not so wide that conclusions change across plausible values;
- boundary errors have been reviewed;
- sensitivity analysis shows that the main study conclusion is stable across the interval;
- the final values were frozen before the confirmatory comparison.

When a metric does not identify a stable replacement, the protocol retains its pre-specified
conservative boundary instead of substituting an overfitted point estimate. The retained
value and the empirical value are compared in the calibration record before the profile is
frozen.
