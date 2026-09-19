package io.github.aidebt.core;

public enum MetricKey {
  AISD("AI-Associated Smell Density"),
  CII("Coupling Instability Index"),
  CDI("Complexity-Documentation Imbalance"),
  HTS("Hyperparameter Transparency Score"),
  CSD("Context Switching Density"),
  RLR("Redundant Logic Ratio"),
  SII("Semantic Inconsistency Index"),
  EGR("Explanation Gap Ratio");

  private final String displayName;

  MetricKey(String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }
}
