package io.github.aidebt.core;

import java.util.Map;

public final class ScoreAggregator {
  private ScoreAggregator() {}

  public static CompositeScores aggregate(Map<MetricKey, MetricValue> metrics, AnalysisConfig config) {
    GroupScore technical = group(metrics, config, MetricKey.AISD, MetricKey.CII, MetricKey.CDI, MetricKey.HTS);
    GroupScore cognitive = group(metrics, config, MetricKey.CSD, MetricKey.RLR, MetricKey.SII, MetricKey.EGR);

    double availableTopWeight = 0.0;
    double adsi = 0.0;
    if (technical.availableWeight > 0) {
      adsi += technical.score * config.tdsiWeight();
      availableTopWeight += config.tdsiWeight();
    }
    if (cognitive.availableWeight > 0) {
      adsi += cognitive.score * config.cogdiWeight();
      availableTopWeight += config.cogdiWeight();
    }
    adsi = availableTopWeight == 0 ? 0 : adsi / availableTopWeight;
    double coverage = (technical.availableWeight + cognitive.availableWeight) / 2.0;
    return new CompositeScores(technical.score, cognitive.score, adsi, coverage);
  }

  private static GroupScore group(Map<MetricKey, MetricValue> metrics, AnalysisConfig config, MetricKey... keys) {
    double weighted = 0.0;
    double available = 0.0;
    for (MetricKey key : keys) {
      MetricValue value = metrics.getOrDefault(key, MetricValue.notApplicable());
      if (value.applicable()) {
        double weight = config.weights().get(key);
        double debtOrientedValue = key == MetricKey.HTS ? 1.0 - value.normalized() : value.normalized();
        weighted += weight * debtOrientedValue;
        available += weight;
      }
    }
    return new GroupScore(available == 0 ? 0 : weighted / available, available);
  }

  private record GroupScore(double score, double availableWeight) {}
}
