package io.github.aidebt.core;

/** Fixed remediation-cost categories based on SonarSource's guidance for non-ABAP/COBOL languages. */
public record RemediationEffortPolicy(int easyMinutes, int moderateMinutes, int majorMinutes, String status) {
  public RemediationEffortPolicy {
    if (easyMinutes < 0 || moderateMinutes < easyMinutes || majorMinutes < moderateMinutes
        || status == null || status.isBlank()) {
      throw new IllegalArgumentException("Effort policy requires ordered non-negative minute values and a status");
    }
  }

  public static RemediationEffortPolicy defaults() {
    return new RemediationEffortPolicy(10, 20, 60, "sonarsource-standard-effort-v1");
  }

  int minutes(RemediationEffortModel.Tier tier) {
    return switch (tier) {
      case EASY -> easyMinutes;
      case MODERATE -> moderateMinutes;
      case MAJOR -> majorMinutes;
    };
  }
}
