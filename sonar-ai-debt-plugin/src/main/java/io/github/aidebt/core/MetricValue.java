package io.github.aidebt.core;

import java.util.Objects;

public record MetricValue(double raw, double normalized, boolean applicable, long numerator, long denominator) {
  public MetricValue {
    if (!Double.isFinite(raw) || !Double.isFinite(normalized)) {
      throw new IllegalArgumentException("Metric values must be finite");
    }
    if (normalized < 0.0 || normalized > 1.0) {
      throw new IllegalArgumentException("Normalized value must be in [0, 1]");
    }
    if (numerator < 0 || denominator < 0) {
      throw new IllegalArgumentException("Counts cannot be negative");
    }
  }

  public static MetricValue ratio(long numerator, long denominator) {
    if (denominator == 0) {
      return notApplicable();
    }
    double value = Math.min(1.0, Math.max(0.0, (double) numerator / denominator));
    return new MetricValue(value, value, true, numerator, denominator);
  }

  public static MetricValue applicable(double raw, double normalized, long numerator, long denominator) {
    return new MetricValue(raw, clamp(normalized), true, numerator, denominator);
  }

  public static MetricValue notApplicable() {
    return new MetricValue(0.0, 0.0, false, 0, 0);
  }

  private static double clamp(double value) {
    return Math.min(1.0, Math.max(0.0, value));
  }
}
