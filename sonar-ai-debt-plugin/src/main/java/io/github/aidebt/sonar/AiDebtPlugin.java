package io.github.aidebt.sonar;

import org.sonar.api.Plugin;

public final class AiDebtPlugin implements Plugin {
  @Override
  public void define(Context context) {
    context.addExtension(AiDebtMetrics.class);
    context.addExtension(AiDebtRules.class);
    context.addExtensions(AiDebtProperties.definitions());
    context.addExtension(AiDebtSensor.class);
    context.addExtension(AiDebtDashboardPage.class);
  }
}
