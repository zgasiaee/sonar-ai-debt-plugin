package io.github.aidebt.core;

import java.util.EnumMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoreAggregatorTest {
  @Test
  void renormalizesWeightsWhenAConditionalMetricIsNotApplicable() {
    EnumMap<MetricKey, MetricValue> values = new EnumMap<>(MetricKey.class);
    for (MetricKey key : MetricKey.values()) values.put(key, MetricValue.applicable(0.4, 0.4, 1, 1));
    values.put(MetricKey.HTS, MetricValue.notApplicable());

    CompositeScores scores = ScoreAggregator.aggregate(values, AnalysisConfig.defaults());

    assertEquals(0.4, scores.tdsi(), 1e-12);
    assertEquals(0.875, scores.coverage(), 1e-12);
  }

  @Test
  void convertsHtsToDebtDirectionOnlyInsideTdsiAggregation() {
    EnumMap<MetricKey, MetricValue> values = new EnumMap<>(MetricKey.class);
    for (MetricKey key : MetricKey.values()) values.put(key, MetricValue.notApplicable());
    values.put(MetricKey.HTS, MetricValue.applicable(0.8, 0.8, 4, 5));

    CompositeScores scores = ScoreAggregator.aggregate(values, AnalysisConfig.defaults());

    assertEquals(0.2, scores.tdsi(), 1e-12);
  }

  @Test
  void rejectsWeightsThatDoNotSumToOne() {
    EnumMap<MetricKey, Double> weights = new EnumMap<>(MetricKey.class);
    for (MetricKey key : MetricKey.values()) weights.put(key, 0.25);
    weights.put(MetricKey.AISD, 0.3);
    assertThrows(IllegalArgumentException.class, () -> new AnalysisConfig(
        weights, 0.5, 0.5, 20, 0.45, 0.85, 0.9, 0.75, 0.4, 5));
  }
}
