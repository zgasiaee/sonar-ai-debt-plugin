package io.github.aidebt.core;

import java.util.EnumMap;
import java.util.Map;

public record AnalysisConfig(
    Map<MetricKey, Double> weights,
    double tdsiWeight,
    double cogdiWeight,
    double aisdScale,
    double contextSimilarityThreshold,
    double syntacticRedundancyThreshold,
    double semanticRedundancyThreshold,
    double semanticConsistencyThreshold,
    double lexicalConsistencyThreshold,
    int complexBlockThreshold) {

  public AnalysisConfig {
    weights = Map.copyOf(weights);
    validateProbability(tdsiWeight, "tdsiWeight");
    validateProbability(cogdiWeight, "cogdiWeight");
    if (Math.abs(tdsiWeight + cogdiWeight - 1.0) > 1e-9) {
      throw new IllegalArgumentException("Top-level weights must sum to one");
    }
    if (aisdScale <= 0 || complexBlockThreshold < 1) {
      throw new IllegalArgumentException("Scale and threshold values must be positive");
    }
    validateProbability(contextSimilarityThreshold, "contextSimilarityThreshold");
    validateProbability(syntacticRedundancyThreshold, "syntacticRedundancyThreshold");
    validateProbability(semanticRedundancyThreshold, "semanticRedundancyThreshold");
    validateProbability(semanticConsistencyThreshold, "semanticConsistencyThreshold");
    validateProbability(lexicalConsistencyThreshold, "lexicalConsistencyThreshold");
    for (MetricKey key : MetricKey.values()) {
      double value = weights.getOrDefault(key, -1.0);
      validateProbability(value, "weight." + key.name());
    }
    validateGroup(weights, MetricKey.AISD, MetricKey.CII, MetricKey.CDI, MetricKey.HTS);
    validateGroup(weights, MetricKey.CSD, MetricKey.RLR, MetricKey.SII, MetricKey.EGR);
  }

  public static AnalysisConfig defaults() {
    EnumMap<MetricKey, Double> weights = new EnumMap<>(MetricKey.class);
    for (MetricKey key : MetricKey.values()) {
      weights.put(key, 0.25);
    }
    return new AnalysisConfig(weights, 0.5, 0.5, 20.0, 0.45, 0.85, 0.90, 0.75, 0.40, 5);
  }

  private static void validateGroup(Map<MetricKey, Double> values, MetricKey... keys) {
    double sum = 0;
    for (MetricKey key : keys) sum += values.get(key);
    if (Math.abs(sum - 1.0) > 1e-9) {
      throw new IllegalArgumentException("Weights in each index must sum to one");
    }
  }

  private static void validateProbability(double value, String name) {
    if (!Double.isFinite(value) || value < 0 || value > 1) {
      throw new IllegalArgumentException(name + " must be in [0, 1]");
    }
  }
}
