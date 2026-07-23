package io.github.aidebt.sonar;

import org.sonar.api.web.page.Page;
import org.sonar.api.web.page.PageDefinition;
import org.sonar.api.web.page.Context;

import static org.sonar.api.web.page.Page.Scope.COMPONENT;

public final class AiDebtDashboardPage implements PageDefinition {
  @Override
  public void define(Context context) {
    context.addPage(Page.builder("aidebt/dashboard").setName("AI Debt").setScope(COMPONENT).build());
  }
}
