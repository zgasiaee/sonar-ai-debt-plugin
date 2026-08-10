package io.github.aidebt.core;

import java.util.EnumMap;
import java.util.Map;

public record AnalysisConfig(
    Map<MetricKey, Double> weights,
    double tdsiWeight,
    double cogdiWeight,
    double aisdScale,
    double contextSimilarityThreshold,
    double csdNamingWeight,
    double csdPatternWeight,
    double csdStructureWeight,
    double syntacticRedundancyThreshold,
    double semanticRedundancyThreshold,
    double rlrBehaviorWeight,
    double rlrCallsWeight,
    double rlrOutputsWeight,
    double semanticConsistencyThreshold,
    double semanticContextThreshold,
    double lexicalConsistencyThreshold,
    int complexBlockThreshold,
    int complexNestingThreshold,
    int mixedFlowComplexityThreshold,
    int mixedFlowKindThreshold,
    int pairBudget) {

  public AnalysisConfig {
    weights = Map.copyOf(weights);
    validateProbability(tdsiWeight, "tdsiWeight");
    validateProbability(cogdiWeight, "cogdiWeight");
    if (Math.abs(tdsiWeight + cogdiWeight - 1.0) > 1e-9) {
      throw new IllegalArgumentException("Top-level weights must sum to one");
    }
    if (aisdScale <= 0 || complexBlockThreshold < 1 || complexNestingThreshold < 1
        || mixedFlowComplexityThreshold < 1 || mixedFlowKindThreshold < 1 || pairBudget < 1) {
      throw new IllegalArgumentException("Scale and threshold values must be positive");
    }
    validateProbability(contextSimilarityThreshold, "contextSimilarityThreshold");
    validateWeightGroup("CSD component weights", csdNamingWeight, csdPatternWeight, csdStructureWeight);
    validateProbability(syntacticRedundancyThreshold, "syntacticRedundancyThreshold");
    validateProbability(semanticRedundancyThreshold, "semanticRedundancyThreshold");
    validateWeightGroup("RLR behavioral component weights", rlrBehaviorWeight, rlrCallsWeight, rlrOutputsWeight);
    validateProbability(semanticConsistencyThreshold, "semanticConsistencyThreshold");
    validateProbability(semanticContextThreshold, "semanticContextThreshold");
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
    return new AnalysisConfig(weights, 0.5, 0.5, 20.0,
        0.45, 1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0,
        0.85, 0.90, 1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0,
        0.75, 0.75, 0.40, 5, 3, 4, 2, 1_000_000);
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

  private static void validateWeightGroup(String name, double... values) {
    double sum = 0.0;
    for (double value : values) {
      validateProbability(value, name);
      sum += value;
    }
    if (Math.abs(sum - 1.0) > 1e-9) {
      throw new IllegalArgumentException(name + " must sum to one");
    }
  }
}
