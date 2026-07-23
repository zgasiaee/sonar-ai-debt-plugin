package io.github.aidebt.core;

/** Source-located, auditable evidence that can also be published as a SonarQube issue. */
public record DebtFinding(
    String file, int line, int endLine, String rule, String evidence, String sourceLine) {
  public DebtFinding(String file, int line, String rule, String evidence) {
    this(file, line, line, rule, evidence, "");
  }

  public DebtFinding(String file, int line, String rule, String evidence, String sourceLine) {
    this(file, line, line, rule, evidence, sourceLine);
  }

  public DebtFinding(String file, int line, int endLine, String rule, String evidence) {
    this(file, line, endLine, rule, evidence, "");
  }

  public DebtFinding {
    if (endLine < line) throw new IllegalArgumentException("endLine cannot precede line");
    sourceLine = sourceLine == null ? "" : sourceLine;
  }
  public FindingCatalog.RuleSpec specification() { return FindingCatalog.rule(rule); }

  public String message() {
    var specification = specification();
    return specification.id() + " · Evidence: " + evidence + " Rationale: " + specification.rationale()
        + " Recommendation: " + FindingCatalog.recommendation(rule, evidence, sourceLine);
  }
}
