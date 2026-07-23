package io.github.aidebt.core;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectAnalyzerTest {
  private final ProjectAnalyzer analyzer = new ProjectAnalyzer(AnalysisConfig.defaults());

  @Test
  void keepsEveryPublishedScoreBounded() {
    AnalysisResult result = analyzer.analyze(List.of(python("sample.py", """
        import requests

        def fetch_items(values=[]):
            # Retry because the remote API is eventually consistent.
            for value in values:
                if value:
                    print(value)
            return values
        """)));

    result.metrics().values().forEach(value -> {
      assertTrue(value.normalized() >= 0.0);
      assertTrue(value.normalized() <= 1.0);
    });
    assertTrue(result.adsi() >= 0.0 && result.adsi() <= 1.0);
    assertTrue(result.metric(MetricKey.AISD).raw() > 0.0);
  }

  @Test
  void hyperparameterDebtUsesDebtDirectionAndHandlesNotApplicable() {
    AnalysisResult implicit = analyzer.analyze(List.of(python("implicit.py", "model = RandomForestClassifier()")));
    AnalysisResult explicit = analyzer.analyze(List.of(python("explicit.py", "model = RandomForestClassifier(n_estimators=100)")));
    AnalysisResult absent = analyzer.analyze(List.of(python("plain.py", "answer = 42")));

    assertEquals(0.0, implicit.metric(MetricKey.HTS).normalized());
    assertEquals(1.0, explicit.metric(MetricKey.HTS).normalized());
    assertFalse(absent.metric(MetricKey.HTS).applicable());
  }

  @Test
  void resolvesAliasedMlConstructorsAndSeparatesConfigurationModes() {
    AnalysisResult result = analyzer.analyze(List.of(python("models.py", """
        from sklearn.ensemble import RandomForestClassifier as RF
        import sklearn.ensemble as ensemble

        implicit = RF()
        explicit = ensemble.RandomForestClassifier(n_estimators=200, random_state=7)
        configured = RF(**model_config)
        """)));

    assertEquals(3.0, result.diagnostic("htd.total"));
    assertEquals(1.0, result.diagnostic("htd.implicit"));
    assertEquals(1.0, result.diagnostic("htd.explicit"));
    assertEquals(0.0, result.diagnostic("htd.config_driven"));
    assertEquals(1.0, result.diagnostic("htd.opaque_config"));
    assertEquals(1.0 / 3.0, result.metric(MetricKey.HTS).normalized(), 0.0001);
    assertTrue(result.artifact("hts.evidence").contains("\"category\":\"implicit\""));
    assertTrue(result.artifact("hts.evidence").contains("\"category\":\"explicit\""));
    assertTrue(result.artifact("hts.evidence").contains("\"category\":\"opaque\""));
    assertTrue(result.artifact("hts.evidence").contains("\"file\":\"models.py\""));
  }

  @Test
  void smellDetectionUsesSyntaxInsteadOfTextInsideCommentsAndStrings() {
    AnalysisResult result = analyzer.analyze(List.of(python("safe.py", """
        # print(value) and eval(value) are discussed here, not executed.
        def describe():
            return "print(value); eval(value)"
        """)));

    assertEquals(0.0, result.diagnostic("aisd.smell.debug-output"));
    assertEquals(0.0, result.diagnostic("aisd.smell.unsafe-eval"));
  }

  @Test
  void detectsSyntacticallyRedundantFunctions() {
    AnalysisResult result = analyzer.analyze(List.of(python("duplicate.py", """
        def sum_positive(items):
            total = 0
            for item in items:
                if item > 0:
                    total += item
            return total

        def add_positive(values):
            result = 0
            for value in values:
                if value > 0:
                    result += value
            return result
        """)));

    assertTrue(result.metric(MetricKey.RLR).applicable());
    assertEquals(1.0, result.metric(MetricKey.RLR).normalized());
    assertTrue(result.artifact("rlr.evidence").contains("\"first\":{\"file\":\"duplicate.py\",\"name\":\"sum_positive\""));
    assertTrue(result.artifact("rlr.evidence").contains("\"second\":{\"file\":\"duplicate.py\",\"name\":\"add_positive\""));
    assertTrue(result.artifact("rlr.evidence").contains("\"matchedBy\":\"syntax"));
  }

  @Test
  void semanticInconsistencyIncludesAssignedVariablesWithEquivalentAstContexts() {
    AnalysisResult result = analyzer.analyze(List.of(python("identifier_context.py", """
        def prepare(items):
            total = sum(items)
            return total

        def publish(values):
            aggregate = sum(values)
            return aggregate
        """)));

    assertTrue(result.artifact("sii.evidence").contains("\"name\":\"total\",\"kind\":\"variable\""));
    assertTrue(result.artifact("sii.evidence").contains("\"name\":\"aggregate\",\"kind\":\"variable\""));
  }

  @Test
  void semanticInconsistencyRejectsStructurallySimilarButConceptuallyDifferentFunctions() {
    AnalysisResult result = analyzer.analyze(List.of(python("different_responsibilities.py", """
        def run_demo(user_id, order, inventory, customer):
            default_model = build_default_forest()
            explicit_model = build_reproducible_forest()
            profile = fetch_remote_profile(user_id)
            decision = route_order(order, inventory, customer)
            return default_model, explicit_model, profile, decision

        def fetch_remote_profile(user_id):
            first = requests.get("/users", params={"id": user_id})
            second = requests.get("/roles", params={"id": user_id})
            third = requests.get("/flags", params={"id": user_id})
            fourth = requests.get("/limits", params={"id": user_id})
            fifth = requests.get("/region", params={"id": user_id})
            return first.json(), second.json(), third.json(), fourth.json(), fifth.json()
        """)));

    assertEquals(0.0, result.metric(MetricKey.SII).normalized());
    assertFalse(result.artifact("sii.evidence").contains("run_demo"));
    assertFalse(result.artifact("sii.evidence").contains("fetch_remote_profile"));
  }

  @Test
  void semanticInconsistencyDoesNotDoubleCountAnRlrCallablePair() {
    AnalysisResult result = analyzer.analyze(List.of(python("duplicate_names.py", """
        def calculate_total(numbers):
            return sum(numbers)

        def compute_sum(values):
            return sum(values)
        """)));

    assertEquals(1.0, result.diagnostic("rlr.redundant"));
    assertEquals(0.0, result.diagnostic("sii.pairs"));
    assertEquals(0.0, result.diagnostic("sii.inconsistent"));
    assertFalse(result.artifact("sii.evidence").contains("calculate_total"));
  }

  @Test
  void explanationGapRequiresRationaleNotAnyComment() {
    String complex = """
        def decide(value):
            # This function checks values.
            if value > 10:
                if value < 20:
                    for item in range(value):
                        if item % 2:
                            value -= 1
            return value
        """;
    String explained = complex.replace("This function checks values.", "We reduce odd values to prevent an invalid downstream state.");

    AnalysisResult gap = analyzer.analyze(List.of(python("gap.py", complex)));
    assertEquals(1.0, gap.metric(MetricKey.EGR).normalized());
    assertTrue(gap.artifact("egr.evidence").contains("\"file\":\"gap.py\",\"name\":\"decide\""));
    assertTrue(gap.artifact("egr.evidence").contains("\"hasRationale\":false"));
    assertEquals(0.0, analyzer.analyze(List.of(python("explained.py", explained))).metric(MetricKey.EGR).normalized());
  }

  @Test
  void explanationGapSelectsDeepNestingBelowTheCyclomaticThreshold() {
    AnalysisResult result = analyzer.analyze(List.of(python("nested.py", """
        def nested_choice(a, b, c):
            if a:
                if b:
                    if c:
                        return 1
            return 0
        """)));

    assertTrue(result.artifact("egr.evidence").contains("\"complexity\":4"));
    assertEquals(1.0, result.diagnostic("egr.nesting_triggered"));
    assertEquals(1.0, result.metric(MetricKey.EGR).normalized());
  }

  @Test
  void explanationRationaleUsesWholeTermsInsteadOfSubstrings() {
    AnalysisResult result = analyzer.analyze(List.of(python("substring.py", """
        def nested_choice(a, b, c):
            # This is a reasonable implementation.
            if a:
                if b:
                    if c:
                        return 1
            return 0
        """)));

    assertEquals(1.0, result.metric(MetricKey.EGR).normalized());
  }

  @Test
  void oneFileAndManyFilesUseTheSameProjectEntryPoint() {
    SourceUnit first = python("one.py", "def one(x):\n    return x if x else 0\n");
    SourceUnit second = python("two.py", "from one import one\n\ndef two(x):\n    return one(x)\n");

    AnalysisResult task = analyzer.analyze(List.of(first));
    AnalysisResult project = analyzer.analyze(List.of(first, second));

    assertEquals(1, task.files());
    assertEquals(2, project.files());
    assertTrue(project.metric(MetricKey.CII).applicable());
    assertEquals(1.0, project.diagnostic("cii.ce"));
    assertEquals(1.0, project.diagnostic("cii.ca"));
    assertEquals(2.0, project.diagnostic("cii.coupled_files"));
    assertTrue(project.artifact("cii.evidence").contains("\"module\":\"one\""));
    assertTrue(project.artifact("cii.evidence").contains("\"line\":1"));
    assertTrue(project.artifact("cii.evidence").contains("\"kind\":\"internal\""));
  }

  @Test
  void publishesAuditableSmellAndFormulaDiagnostics() {
    AnalysisResult result = analyzer.analyze(List.of(python("diagnostic.py", """
        def risky(values=[]):
            print(values)
            try:
                return eval(values[0])
            except Exception:
                pass
        """)));

    assertEquals(result.metric(MetricKey.AISD).numerator(), result.diagnostic("aisd.smells"));
    assertEquals(1.0, result.diagnostic("aisd.smell.mutable-default"));
    assertEquals(1.0, result.diagnostic("aisd.smell.debug-output"));
    assertEquals(1.0, result.diagnostic("aisd.smell.unsafe-eval"));
    assertEquals(1.0, result.diagnostic("aisd.smell.broad-except"));
    assertEquals(0.25, result.diagnostic("weight.aisd"));
  }

  @Test
  void allMetricsRegressionInputProducesANonZeroSignalForEveryMetric() {
    List<SourceUnit> sources = List.of(
        python("src/app.py", """
            from models import build_default_forest, build_reproducible_forest
            from services import fetch_remote_profile, route_order

            def run_demo(user_id, order, inventory, customer):
                default_model = build_default_forest()
                explicit_model = build_reproducible_forest()
                profile = fetch_remote_profile(user_id)
                decision = route_order(order, inventory, customer)
                return default_model, explicit_model, profile, decision
            """),
        python("src/models.py", """
            from sklearn.ensemble import RandomForestClassifier

            def build_default_forest():
                return RandomForestClassifier()

            def build_reproducible_forest():
                return RandomForestClassifier(n_estimators=80, max_depth=6, random_state=17)
            """),
        python("src/services.py", """
            import numpy as np
            import pandas as pd
            import requests

            def total_positive_values(items):
                total = 0
                for item in items:
                    if item > 0:
                        total += item
                return total

            def sum_approved_amounts(records):
                result = 0
                for record in records:
                    if record > 0:
                        result += record
                return result

            def summarize_prices(prices):
                total = sum(prices)
                return {"amount": total, "count": len(prices)}

            def summarize_scores(scores):
                aggregate = sum(scores)
                return aggregate / max(1, len(scores))

            def fetch_remote_profile(user_id):
                first = requests.get("https://example.invalid/users", params={"id": user_id})
                second = requests.get("https://example.invalid/roles", params={"id": user_id})
                third = requests.get("https://example.invalid/flags", params={"id": user_id})
                fourth = requests.get("https://example.invalid/limits", params={"id": user_id})
                fifth = requests.get("https://example.invalid/region", params={"id": user_id})
                return first.json(), second.json(), third.json(), fourth.json(), fifth.json()

            def load_implicit_schema(path):
                frame = pd.read_csv(path)
                frame["pending"] = 0
                values = frame.values
                return values == np.nan

            def route_order(order, inventory, customer):
                \"\"\"Allocate only after all guards pass because partial allocation would violate stock consistency.\"\"\"
                if order:
                    if inventory:
                        for item in order:
                            if item in inventory:
                                if customer:
                                    while inventory[item] > 0:
                                        inventory[item] -= 1
                                        if inventory[item] == 0:
                                            return "allocated"
                return "rejected"
            """),
        python("src/style_transition.py", """
            def select_even_values(values):
                return [value for value in values if value % 2 == 0]

            async def processBatchWithRetries(client, jobs, retryLimit):
                pending = list(jobs)
                attempts = 0
                while pending:
                    try:
                        job = pending.pop(0)
                        response = await client.send(job)
                        if response.failed:
                            pending.append(job)
                        attempts += 1
                    except TimeoutError:
                        attempts += 1
                        if attempts >= retryLimit:
                            raise
                return pending
            """));

    AnalysisResult result = analyzer.analyze(sources);

    for (MetricKey key : MetricKey.values()) {
      assertTrue(result.metric(key).applicable(), () -> key + " should be applicable");
      assertTrue(result.metric(key).normalized() > 0.0, () -> key + " should be non-zero");
    }
    assertTrue(result.diagnostic("cii.ca") > 0.0);
    assertTrue(result.diagnostic("cii.ce") > 0.0);
    assertEquals(2.0, result.diagnostic("htd.total"));
    assertEquals(1.0, result.diagnostic("htd.implicit"));
    assertTrue(result.diagnostic("csd.switches") > 0.0);
    assertTrue(result.diagnostic("rlr.redundant") > 0.0);
    assertTrue(result.diagnostic("sii.inconsistent") > 0.0);
    assertTrue(result.diagnostic("egr.unexplained") > 0.0);
    assertEquals(2.0, result.diagnostic("egr.complex"));
    assertEquals(1.0, result.diagnostic("egr.unexplained"));
    assertTrue(result.artifact("cdi.evidence").contains("\"blocks\":["));
  }

  @Test
  void specDetectEvidenceIsTheExclusiveAisdNumeratorInProductionMode() {
    SourceUnit source = python("sample.py", "def risky(values=[]):\n    print(values)\n");
    List<DebtFinding> external = List.of(
        new DebtFinding("sample.py", 1, "specdetect-r2", "Random Seed Not Set at line 1"),
        new DebtFinding("sample.py", 2, "specdetect-r5", "Hyperparameter not explicitly set at line 2"));

    AnalysisResult result = analyzer.analyzeWithSpecDetect(List.of(source), external);

    assertEquals(2.0, result.diagnostic("aisd.smells"));
    assertEquals(2L, result.metric(MetricKey.AISD).numerator());
    assertEquals(1.0, result.diagnostic("aisd.smell.specdetect-r2"));
    assertEquals(1.0, result.diagnostic("aisd.smell.specdetect-r5"));
  }

  @Test
  void missingSpecDetectReportMakesAisdNotApplicableInsteadOfZero() {
    AnalysisResult result = analyzer.analyzeWithoutSpecDetect(
        List.of(python("sample.py", "def clean():\n    return 1\n")));

    assertFalse(result.metric(MetricKey.AISD).applicable());
  }

  @Test
  void aisdCountsDistinctOccurrencesButDeduplicatesTheSameFileLineAndRule() {
    SourceUnit first = python("first.py", "def first():\n    return 1\n");
    SourceUnit second = python("second.py", "def second():\n    return 2\n");
    List<DebtFinding> findings = List.of(
        new DebtFinding("first.py", 1, "specdetect-r12", "first report"),
        new DebtFinding("first.py", 1, "specdetect-r12", "duplicate report"),
        new DebtFinding("first.py", 2, "specdetect-r12", "another line"),
        new DebtFinding("second.py", 1, "specdetect-r12", "another file"));

    AnalysisResult result = analyzer.analyzeWithSpecDetect(List.of(first, second), findings);

    assertEquals(3L, result.metric(MetricKey.AISD).numerator());
    assertEquals(3.0, result.diagnostic("aisd.smell.specdetect-r12"));
  }

  @Test
  void csdPublishesCompactNumericEvidenceForDetectedSwitches() {
    AnalysisResult result = analyzer.analyze(List.of(python("contexts.py", """
        def calculate(value):
            return value + 1

        def fetch_remote(url):
            try:
                response = requests.get(url)
                response.raise_for_status()
            except NetworkError:
                raise
        """)));

    assertEquals(1.0, result.diagnostic("csd.transitions"));
    assertEquals(1.0, result.diagnostic("csd.switches"));
    String evidence = result.artifact("csd.evidence");
    assertTrue(evidence.contains("\"from\":{\"name\":\"calculate\""));
    assertTrue(evidence.contains("\"to\":{\"name\":\"fetch_remote\""));
    assertTrue(evidence.contains("\"namingStyle\":"));
    assertTrue(evidence.contains("\"codingPatterns\":"));
    assertTrue(evidence.contains("\"structuralShape\":"));
    assertTrue(evidence.contains("\"switches\":["));
    assertFalse(evidence.contains("\"changedFeatures\":"));
  }

  @Test
  void csdOnlyComparesAdjacentCallablesWithinTheSameLexicalScope() {
    AnalysisResult result = analyzer.analyze(List.of(python("scopes.py", """
        def module_first():
            return 1

        class Worker:
            def only_method(self):
                return 2

        def module_second():
            return 3
        """)));

    assertEquals(1.0, result.diagnostic("csd.transitions"));
    assertEquals(0.0, result.diagnostic("csd.switches"), result.artifact("csd.evidence"));
    String evidence = result.artifact("csd.evidence");
    assertTrue(evidence.contains("\"switches\":[]"));
    assertFalse(evidence.contains("only_method"));
  }

  @Test
  void csdDoesNotTreatDifferentDomainVocabularyAsAStyleSwitch() {
    AnalysisResult result = analyzer.analyze(List.of(python("consistent_style.py", """
        def collect_active_users(users):
            selected = []
            for user in users:
                if user.active:
                    selected.append(user)
            return selected

        def collect_overdue_invoices(invoices):
            selected = []
            for invoice in invoices:
                if invoice.overdue:
                    selected.append(invoice)
            return selected
        """)));

    assertEquals(1.0, result.diagnostic("csd.transitions"));
    assertEquals(0.0, result.diagnostic("csd.switches"));
  }

  @Test
  void csdDoesNotConfuseLocalComputationWithNetworkResponsibility() {
    AnalysisResult result = analyzer.analyze(List.of(python("responsibilities.py", """
        def sum_approved_amounts(records):
            result = 0
            for record in records:
                if record > 0:
                    result += record
            return result

        def fetch_remote_profile(user_id):
            first = requests.get("https://example.invalid/users", params={"id": user_id})
            second = requests.get("https://example.invalid/roles", params={"id": user_id})
            third = requests.get("https://example.invalid/flags", params={"id": user_id})
            fourth = requests.get("https://example.invalid/limits", params={"id": user_id})
            fifth = requests.get("https://example.invalid/region", params={"id": user_id})
            print("profile fetched", user_id)
            return first.json(), second.json(), third.json(), fourth.json(), fifth.json()
        """)));

    assertEquals(1.0, result.diagnostic("csd.transitions"));
    assertEquals(0.0, result.diagnostic("csd.switches"), result.artifact("csd.evidence"));
  }

  @Test
  void csdDetectsAnIntentionalMultiDimensionalStyleBreak() {
    AnalysisResult result = analyzer.analyze(List.of(python("style_transition.py", """
        def select_even_values(values):
            return [value for value in values if value % 2 == 0]

        async def processBatchWithRetries(client, jobs, retryLimit):
            pending = list(jobs)
            attempts = 0
            while pending:
                try:
                    job = pending.pop(0)
                    response = await client.send(job)
                    if response.failed:
                        pending.append(job)
                    attempts += 1
                except TimeoutError:
                    attempts += 1
                    if attempts >= retryLimit:
                        raise
            return pending
        """)));

    assertEquals(1.0, result.diagnostic("csd.transitions"));
    assertEquals(1.0, result.diagnostic("csd.switches"));
    assertTrue(result.artifact("csd.evidence").contains("select_even_values"));
    assertTrue(result.artifact("csd.evidence").contains("processBatchWithRetries"));
  }

  private static SourceUnit python(String path, String code) {
    return new SourceUnit(path, code, SourceUnit.Language.PYTHON);
  }

}
