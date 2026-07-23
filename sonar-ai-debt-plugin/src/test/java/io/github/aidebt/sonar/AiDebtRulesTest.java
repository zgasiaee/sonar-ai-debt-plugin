package io.github.aidebt.sonar;

import org.junit.jupiter.api.Test;
import org.sonar.api.server.rule.RulesDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiDebtRulesTest {
  @Test
  void registersEveryPublishableFindingAsAnActivePythonRule() {
    RulesDefinition.Context context = new RulesDefinition.Context();
    context.setCurrentPluginKey("aidebt");

    new AiDebtRules().define(context);

    var repository = context.repository(AiDebtRules.REPOSITORY);
    assertNotNull(repository);
    assertEquals("py", repository.language());
    assertEquals(AiDebtRules.RULES.size(), repository.rules().size());
    assertTrue(repository.rules().stream().allMatch(RulesDefinition.Rule::activatedByDefault));
  }
}
