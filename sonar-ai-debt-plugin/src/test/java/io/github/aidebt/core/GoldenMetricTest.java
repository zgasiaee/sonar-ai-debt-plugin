package io.github.aidebt.core;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Small hand-computable fixtures that freeze metric numerators and denominators. */
class GoldenMetricTest {
  private final ProjectAnalyzer analyzer = new ProjectAnalyzer(AnalysisConfig.defaults());

  @Test
  void packageAwareCouplingResolvesRelativeImports() {
    AnalysisResult result = analyzer.analyze(List.of(
        py("pkg/a.py", "def work(value):\n    return value\n"),
        py("pkg/b.py", "from .a import work\n\ndef use(value):\n    return work(value)\n")));

    assertEquals(1.0, result.diagnostic("cii.ce"));
    assertEquals(1.0, result.diagnostic("cii.ca"));
    assertEquals(1.0, result.diagnostic("cii.internal_dependencies"));
    assertEquals(0.0, result.diagnostic("cii.external_dependencies"));
    assertEquals(0.5, result.metric(MetricKey.CII).normalized(), 0.0001);
  }

  @Test
  void cdiUsesAstComplexityAndAuditableDocumentationCounts() {
    AnalysisResult result = analyzer.analyze(List.of(py("decision.py", """
        def decide(value):
            # Return early because negative values are invalid downstream.
            if value < 0:
                return 0
            return value
        """)));

    assertEquals(2.0, result.diagnostic("cdi.mean_complexity"));
    assertEquals(1.0, result.diagnostic("cdi.documented_blocks"));
    assertEquals(1.0, result.diagnostic("cdi.documentation_coverage"));
    assertEquals(0.0, result.metric(MetricKey.CDI).raw(), 0.0001);
    assertEquals(0.0, result.metric(MetricKey.CDI).normalized(), 0.0001);
    assertTrue(result.artifact("cdi.evidence").contains("\"commentLines\":1"));
  }

  @Test
  void cdiHasNoSizeDependentContinuityCorrectionAndKeepsFunctionBoundaries() {
    AnalysisResult result = analyzer.analyze(List.of(py("functions.py", """
        def undocumented(value):
            if value:
                return value
            return 0

        def next_function():
            return 1
        """)));

    assertEquals(0.0, result.diagnostic("cdi.comment_density"), 0.0001);
    assertTrue(result.metric(MetricKey.CDI).raw() > 0.0);
    assertTrue(result.artifact("cdi.evidence").contains("\"endLine\":4"));
    assertTrue(result.artifact("cdi.evidence").contains("\"startLine\":6"));
  }

  @Test
  void resolvedAndOpaqueConfigurationsRemainDistinguishable() {
    AnalysisResult result = analyzer.analyze(List.of(py("configuration.py", """
        from sklearn.ensemble import RandomForestClassifier

        stable = {"n_estimators": 100, "random_state": 11}
        first = RandomForestClassifier(**stable)
        second = RandomForestClassifier(**runtime_config)
        """)));

    assertEquals(1.0, result.diagnostic("htd.config_driven"));
    assertEquals(1.0, result.diagnostic("htd.opaque_config"));
    assertEquals(1.0, result.diagnostic("htd.missing_seed"));
    assertEquals(0.5, result.metric(MetricKey.HTS).normalized());
    assertTrue(result.findings().stream().anyMatch(finding -> finding.rule().equals("opaque-ml-config")));
  }

  @Test
  void astSmellsCoverPlaceholdersSwallowedErrorsAndLeakageCandidates() {
    AnalysisResult result = analyzer.analyze(List.of(py("risks.py", """
        def unfinished():
            ...

        def leak(scaler, x_test):
            try:
                return scaler.fit_transform(x_test)
            except Exception:
                pass
        """)));

    assertEquals(1.0, result.diagnostic("aisd.smell.swallowed-exception"));
    assertEquals(1.0, result.diagnostic("aisd.smell.training-on-evaluation-data"));
    assertTrue(result.diagnostic("aisd.smell.placeholder") >= 2.0);
  }

  @Test
  void docstringRationaleClosesTheExplanationGap() {
    AnalysisResult result = analyzer.analyze(List.of(py("explained.py", """
        def normalize(values):
            \"\"\"Clamp values because downstream probabilities must remain within the valid invariant.\"\"\"
            for index, value in enumerate(values):
                if value < 0:
                    if value < -10:
                        values[index] = 0
                elif value > 1:
                    values[index] = 1
            return values
        """)));

    assertEquals(1.0, result.diagnostic("egr.complex"));
    assertEquals(0.0, result.metric(MetricKey.EGR).normalized());
  }

  @Test
  void semanticInconsistencyUsesBehaviorOutputsAndIdentifierIntent() {
    AnalysisResult result = analyzer.analyze(List.of(py("names.py", """
        def calculate_total(amount):
            if amount > 0:
                return amount
            return 0

        def compute_sum(distance):
            result = 0
            if distance > 0:
                result = distance
            return result
        """)));

    assertEquals(1.0, result.diagnostic("sii.pairs"));
    assertEquals(1.0, result.diagnostic("sii.inconsistent"));
    assertTrue(result.artifact("sii.evidence").contains("\"name\":\"calculate_total\",\"kind\":\"function\""));
    assertTrue(result.artifact("sii.evidence").contains("\"name\":\"compute_sum\",\"kind\":\"function\""));
    assertTrue(result.artifact("sii.evidence").contains("\"lexical\":"));
  }

  @Test
  void detectsEmpiricallyObservedCopilotPythonSmellFamiliesFromAst() {
    AnalysisResult result = analyzer.analyze(List.of(py("copilot_smells.py", """
        deeply_nested = [[[[1]]]]

        def configure(a, b, c, d, e, f):
            return a

        chained = service.client.session.response.payload.value
        complex_values = [value for row in rows for value in row if value if value > 0]
        """)));

    assertEquals(1.0, result.diagnostic("aisd.smell.multiply-nested-container"));
    assertEquals(1.0, result.diagnostic("aisd.smell.long-parameter-list"));
    assertEquals(1.0, result.diagnostic("aisd.smell.long-message-chain"));
    assertEquals(1.0, result.diagnostic("aisd.smell.complex-comprehension"));
  }

  private static SourceUnit py(String path, String source) {
    return new SourceUnit(path, source, SourceUnit.Language.PYTHON);
  }
}
