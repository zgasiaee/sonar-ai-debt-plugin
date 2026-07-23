package io.github.aidebt.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;

public final class AnalysisResult {
  private final Map<MetricKey, MetricValue> metrics;
  private final double tdsi;
  private final double cogdi;
  private final double adsi;
  private final double coverage;
  private final int files;
  private final int lines;
  private final Map<String, Double> diagnostics;
  private final List<DebtFinding> findings;
  private final Map<String, String> artifacts;

  public AnalysisResult(Map<MetricKey, MetricValue> metrics, CompositeScores scores, int files, int lines,
                        Map<String, Double> diagnostics, List<DebtFinding> findings,
                        Map<String, String> artifacts) {
    this.metrics = Collections.unmodifiableMap(new EnumMap<>(metrics));
    this.tdsi = scores.tdsi();
    this.cogdi = scores.cogdi();
    this.adsi = scores.adsi();
    this.coverage = scores.coverage();
    this.files = files;
    this.lines = lines;
    this.diagnostics = Map.copyOf(diagnostics);
    this.findings = List.copyOf(findings);
    this.artifacts = Map.copyOf(artifacts);
  }

  public Map<MetricKey, MetricValue> metrics() { return metrics; }
  public MetricValue metric(MetricKey key) { return metrics.getOrDefault(key, MetricValue.notApplicable()); }
  public double tdsi() { return tdsi; }
  public double cogdi() { return cogdi; }
  public double adsi() { return adsi; }
  public double coverage() { return coverage; }
  public int files() { return files; }
  public int lines() { return lines; }
  public Map<String, Double> diagnostics() { return diagnostics; }
  public double diagnostic(String key) { return diagnostics.getOrDefault(key, 0.0); }
  public List<DebtFinding> findings() { return findings; }
  public String artifact(String key) { return artifacts.getOrDefault(key, ""); }
}
