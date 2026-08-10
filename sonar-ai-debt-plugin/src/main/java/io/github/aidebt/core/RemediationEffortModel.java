package io.github.aidebt.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a standardized remediation-cost estimate from concrete remediation actions.
 */
final class RemediationEffortModel {
  static final int MINUTES_PER_DAY = 480;

  enum Tier {
    EASY, MODERATE, MAJOR
  }

  record Action(MetricKey metric, String remediationKey, Tier tier) {
    Action {
      if (metric == null || remediationKey == null || remediationKey.isBlank() || tier == null) {
        throw new IllegalArgumentException("A remediation action requires a metric, stable key, and effort tier");
      }
    }
  }

  record Estimate(boolean estimable, int actions, int easyActions, int moderateActions, int majorActions,
                  int minutes, String reason) {}

  private RemediationEffortModel() {}

  static Action action(MetricKey metric, String key, Tier tier) {
    return new Action(metric, key, tier);
  }

  static String report(List<Action> actions, Map<MetricKey, MetricValue> metrics,
                       RemediationEffortPolicy policy) {
    EnumMap<MetricKey, Estimate> estimates = new EnumMap<>(MetricKey.class);
    for (MetricKey key : MetricKey.values()) {
      MetricValue value = metrics.get(key);
      if (value == null || !value.applicable()) {
        estimates.put(key, unavailable("metric-not-applicable"));
      } else {
        estimates.put(key, estimate(actions.stream().filter(action -> action.metric() == key).toList(), policy));
      }
    }

    List<MetricKey> technicalKeys = List.of(MetricKey.AISD, MetricKey.CII, MetricKey.CDI, MetricKey.HTS);
    List<MetricKey> cognitiveKeys = List.of(MetricKey.CSD, MetricKey.RLR, MetricKey.SII, MetricKey.EGR);
    List<MetricKey> allKeys = List.of(MetricKey.values());
    Estimate technical = aggregate(actions, technicalKeys, estimates, policy);
    Estimate cognitive = aggregate(actions, cognitiveKeys, estimates, policy);
    Estimate overall = aggregate(actions, allKeys, estimates, policy);
    int rawActions = estimates.values().stream().filter(Estimate::estimable).mapToInt(Estimate::actions).sum();
    long applicableMetrics = metrics.values().stream().filter(MetricValue::applicable).count();
    long estimableMetrics = estimates.values().stream().filter(Estimate::estimable).count();

    List<String> metricJson = new ArrayList<>();
    for (MetricKey key : MetricKey.values()) {
      metricJson.add("\"" + key.name() + "\":" + estimateJson(estimates.get(key)));
    }
    return "{\"version\":\"action-effort-policy-v3\",\"policySource\":\"" + json(policy.status()) + "\""
        + ",\"minutesPerDay\":" + MINUTES_PER_DAY
        + ",\"tierMinutes\":{\"easy\":" + policy.easyMinutes()
        + ",\"moderate\":" + policy.moderateMinutes() + ",\"major\":" + policy.majorMinutes() + "}"
        + ",\"deduplication\":\"stable-remediation-action-key\""
        + ",\"applicableMetrics\":" + applicableMetrics + ",\"estimableMetrics\":" + estimableMetrics
        + ",\"rawMetricActions\":" + rawActions + ",\"uniqueOverallActions\":" + overall.actions()
        + ",\"metrics\":{" + String.join(",", metricJson) + "}"
        + ",\"technical\":" + estimateJson(technical)
        + ",\"cognitive\":" + estimateJson(cognitive)
        + ",\"overall\":" + estimateJson(overall) + "}";
  }

  private static Estimate aggregate(List<Action> actions, List<MetricKey> included,
                                    Map<MetricKey, Estimate> estimates, RemediationEffortPolicy policy) {
    boolean anyEstimable = included.stream().map(estimates::get).anyMatch(Estimate::estimable);
    if (!anyEstimable) return unavailable("no-estimable-metric-in-group");
    return estimate(actions.stream().filter(action -> included.contains(action.metric())).toList(), policy);
  }

  private static Estimate estimate(List<Action> actions, RemediationEffortPolicy policy) {
    Map<String, Tier> unique = new LinkedHashMap<>();
    for (Action action : actions) {
      unique.merge(action.remediationKey(), action.tier(), RemediationEffortModel::largerTier);
    }
    int minutes = 0, easy = 0, moderate = 0, major = 0;
    for (Tier tier : unique.values()) {
      minutes += policy.minutes(tier);
      if (tier == Tier.EASY) easy++;
      else if (tier == Tier.MODERATE) moderate++;
      else major++;
    }
    return new Estimate(true, unique.size(), easy, moderate, major, minutes, "");
  }

  private static Estimate unavailable(String reason) {
    return new Estimate(false, 0, 0, 0, 0, 0, reason);
  }

  private static Tier largerTier(Tier left, Tier right) {
    return left.ordinal() >= right.ordinal() ? left : right;
  }

  private static String estimateJson(Estimate estimate) {
    return "{\"estimable\":" + estimate.estimable() + ",\"actions\":" + estimate.actions()
        + ",\"tierCounts\":{\"easy\":" + estimate.easyActions() + ",\"moderate\":"
        + estimate.moderateActions() + ",\"major\":" + estimate.majorActions() + "}"
        + ",\"minutes\":" + estimate.minutes() + ",\"reason\":\"" + json(estimate.reason()) + "\"}";
  }

  private static String json(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r");
  }
}
