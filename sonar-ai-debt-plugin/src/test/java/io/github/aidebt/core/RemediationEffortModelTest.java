package io.github.aidebt.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class RemediationEffortModelTest {
  @Test
  void deduplicatesOneRepairSharedByTwoMetrics() {
    EnumMap<MetricKey, MetricValue> metrics = applicableMetrics();
    String report = RemediationEffortModel.report(List.of(
        RemediationEffortModel.action(MetricKey.CDI, "documentation:a.py:1:8",
            RemediationEffortModel.Tier.EASY),
        RemediationEffortModel.action(MetricKey.EGR, "documentation:a.py:1:8",
            RemediationEffortModel.Tier.MODERATE)), metrics, RemediationEffortPolicy.defaults());

    assertTrue(report.contains("\"rawMetricActions\":2"), report);
    assertTrue(report.contains("\"uniqueOverallActions\":1"), report);
    assertTrue(report.contains("\"overall\":{\"estimable\":true,\"actions\":1,\"tierCounts\":{\"easy\":0,\"moderate\":1,\"major\":0},\"minutes\":20"), report);
  }

  @Test
  void reportsZeroWhenApplicableCiiHasNoActionableDependencyProblem() {
    EnumMap<MetricKey, MetricValue> metrics = new EnumMap<>(MetricKey.class);
    for (MetricKey key : MetricKey.values()) metrics.put(key, MetricValue.notApplicable());
    metrics.put(MetricKey.CII, MetricValue.applicable(0.75, 0.75, 6, 8));

    String report = RemediationEffortModel.report(List.of(), metrics, RemediationEffortPolicy.defaults());

    assertTrue(report.contains("\"CII\":{\"estimable\":true,\"actions\":0"), report);
    assertTrue(report.contains("\"technical\":{\"estimable\":true,\"actions\":0"), report);
  }

  @Test
  void estimatesCiiFromConcreteDependencyRepairActions() {
    EnumMap<MetricKey, MetricValue> metrics = new EnumMap<>(MetricKey.class);
    for (MetricKey key : MetricKey.values()) metrics.put(key, MetricValue.notApplicable());
    metrics.put(MetricKey.CII, MetricValue.applicable(0.75, 0.75, 6, 8));

    String report = RemediationEffortModel.report(List.of(
        RemediationEffortModel.action(MetricKey.CII, "cycle:a|b", RemediationEffortModel.Tier.MAJOR),
        RemediationEffortModel.action(MetricKey.CII, "edge:c->d", RemediationEffortModel.Tier.MODERATE)),
        metrics, RemediationEffortPolicy.defaults());

    assertTrue(report.contains("\"CII\":{\"estimable\":true,\"actions\":2"), report);
    assertTrue(report.contains("\"minutes\":80"), report);
  }

  private static EnumMap<MetricKey, MetricValue> applicableMetrics() {
    EnumMap<MetricKey, MetricValue> metrics = new EnumMap<>(MetricKey.class);
    for (MetricKey key : MetricKey.values()) metrics.put(key, MetricValue.applicable(0.5, 0.5, 1, 2));
    return metrics;
  }
}
