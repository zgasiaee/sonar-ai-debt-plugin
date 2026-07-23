package io.github.aidebt.sonar;

import java.util.Map;
import io.github.aidebt.core.FindingCatalog;
import org.sonar.api.issue.impact.SoftwareQuality;
import org.sonar.api.issue.impact.Severity;
import org.sonar.api.rules.CleanCodeAttribute;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition;

/** SonarQube rule metadata for every source-located AI Debt finding. */
public final class AiDebtRules implements RulesDefinition {
  public static final String REPOSITORY = "aidebt-python";
  static final Map<String, FindingCatalog.RuleSpec> RULES = FindingCatalog.rules();

  @Override
  public void define(Context context) {
    NewRepository repository = context.createRepository(REPOSITORY, "py").setName("AI Debt Python");
    RULES.forEach((key, specification) -> repository.createRule(key)
        .setName(specification.id() + " · " + specification.title())
        .setMarkdownDescription("**Rationale:** " + specification.rationale()
            + "\n\n**Recommendation:** " + specification.remediation())
        .setType(RuleType.CODE_SMELL)
        .setCleanCodeAttribute(CleanCodeAttribute.COMPLETE)
        .addDefaultImpact(SoftwareQuality.MAINTAINABILITY, Severity.valueOf(specification.severity()))
        .setActivatedByDefault(true));
    repository.done();
  }

}
