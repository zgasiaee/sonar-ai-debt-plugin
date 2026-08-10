# Architecture

## Analysis flow

1. SpecDetect4AI runs before the scanner and writes its native R1–R24 findings to JSON.
2. `AiDebtSensor` selects supported main-source files and ingests that report as the exclusive AISD evidence source.
3. `PythonAstParser` parses each `.py` file with SonarPython's grammar and extracts functions, control flow, calls, imports, aliases, comments, and source locations without executing project code.
4. `PythonMlRegistry` classifies framework calls and reproducibility-sensitive parameters independently of the metric formula.
5. `ProjectAnalyzer` computes the eight raw metrics over one collection of files and retains source-located evidence.
6. `ScoreAggregator` combines applicable normalized components and reports applicability coverage.
7. The sensor publishes scores, diagnostic measures, and line-level SonarQube issues for validated rule candidates.
8. The project-scoped dashboard reads the measures through SonarQube's Web API and reconstructs the worked formulas for auditability.

The metric engine is separated from the scanner adapter. Its parser depends on the public SonarPython API, while metric calculation remains independent of SonarQube sensor state. This keeps tests deterministic and leaves room for a future CLI or batch-study adapter without duplicating formulas.

## Design boundaries

- The plugin is intentionally Python-only. SonarPython supplies the complete language grammar; our visitor extracts only the semantic facts required by the metrics.
- Imports and aliases are resolved within the analyzed source set. External imports affect efferent coupling but cannot create afferent coupling inside the project.
- Function representations retain normalized syntax, control behavior, nesting, call families, parameters, identifiers, output names, comments, and docstrings. Cognitive metrics combine these deterministic views instead of requiring a network model during a scan.
- Literal configuration dictionaries expanded with `**config` are traced to their keys. Dynamic or externally loaded configurations remain explicitly marked opaque rather than being assumed transparent.
- Source code is never executed and the plugin performs no network calls during analysis.
- Pairwise metrics analyze up to 1,000,000 deterministic round-robin pairs per metric. This covers every pair for approximately 1,414 items and, when capped, distributes comparisons across the complete source set instead of favoring early files. The analyzed-pair denominator and cap status are retained in the metric value model.
- Composite measures are project-level; smell and reproducibility evidence is also emitted as line-level Python issues.
- Dashboard scripts are bundled as static plugin resources and use DOM text nodes rather than HTML interpolation for server-provided values.
