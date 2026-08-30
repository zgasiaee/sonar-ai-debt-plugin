package io.github.aidebt.sonar;

import java.util.ArrayList;
import java.util.List;
import org.sonar.api.PropertyType;
import org.sonar.api.config.PropertyDefinition;

public final class AiDebtProperties {
  public static final String PREFIX = "sonar.aidebt.";
  public static final String ENABLED = PREFIX + "enabled";
  public static final String TDSI_WEIGHTS = PREFIX + "tdsi.weights";
  public static final String COGDI_WEIGHTS = PREFIX + "cogdi.weights";
  public static final String INDEX_WEIGHTS = PREFIX + "index.weights";
  public static final String CSD_SIMILARITY_THRESHOLD = PREFIX + "csd.similarityThreshold";
  public static final String CSD_COMPONENT_WEIGHTS = PREFIX + "csd.componentWeights";
  public static final String RLR_SYNTAX_THRESHOLD = PREFIX + "rlr.syntaxSimilarityThreshold";
  public static final String RLR_BEHAVIOR_THRESHOLD = PREFIX + "rlr.behaviorSimilarityThreshold";
  public static final String RLR_BEHAVIOR_COMPONENT_WEIGHTS = PREFIX + "rlr.behaviorComponentWeights";
  public static final String SII_CONCEPT_THRESHOLD = PREFIX + "sii.conceptSimilarityThreshold";
  public static final String SII_CONTEXT_THRESHOLD = PREFIX + "sii.contextSimilarityThreshold";
  public static final String SII_NAME_THRESHOLD = PREFIX + "sii.nameSimilarityCeiling";
  public static final String EGR_COMPLEXITY_THRESHOLD = PREFIX + "egr.complexityThreshold";
  public static final String EGR_NESTING_THRESHOLD = PREFIX + "egr.nestingThreshold";
  public static final String EGR_MIXED_FLOW_COMPLEXITY_THRESHOLD = PREFIX + "egr.mixedFlowComplexityThreshold";
  public static final String EGR_MIXED_FLOW_KIND_THRESHOLD = PREFIX + "egr.mixedFlowKindThreshold";
  public static final String PAIR_BUDGET = PREFIX + "pairBudget";
  public static final String SPECDETECT_REPORT = PREFIX + "specdetect.reportPath";
  public static final String CALIBRATION_EXPORT_PATH = PREFIX + "calibration.exportPath";
  public static final String CALIBRATION_GROUP_ID = PREFIX + "calibration.groupId";

  private AiDebtProperties() {}

  public static List<PropertyDefinition> definitions() {
    List<PropertyDefinition> values = new ArrayList<>();
    values.add(PropertyDefinition.builder(ENABLED).name("Enable AI Debt analysis").description("Analyze supported main source files.")
        .category("AI Debt").type(PropertyType.BOOLEAN).defaultValue("true").build());
    values.add(csv(TDSI_WEIGHTS, "TDSI weights", "AISD,CII,CDI,HTS-debt-contribution weights; must be non-negative and sum to one.", "0.25,0.25,0.25,0.25"));
    values.add(csv(COGDI_WEIGHTS, "CogDI weights", "CSD,RLR,SII,EGR weights; must be non-negative and sum to one.", "0.25,0.25,0.25,0.25"));
    values.add(csv(INDEX_WEIGHTS, "Final index weights", "TDSI,CogDI weights; must sum to one.", "0.5,0.5"));
    values.add(PropertyDefinition.builder(CSD_SIMILARITY_THRESHOLD).name("CSD context-similarity threshold")
        .description("Adjacent same-scope callables below this similarity are counted as context switches. "
            + "The default belongs to the frozen consensus-adjudicated-v1 profile.")
        .category("AI Debt").type(PropertyType.FLOAT).defaultValue("0.45").build());
    values.add(csv(CSD_COMPONENT_WEIGHTS, "CSD component weights",
        "Naming-style,coding-pattern,structural-shape weights; must sum to one.",
        "0.333333333333,0.333333333333,0.333333333334"));
    values.add(calibratedFloat(RLR_SYNTAX_THRESHOLD, "RLR syntax-similarity threshold",
        "Callable pairs at or above this normalized-token similarity are redundancy candidates.", "0.85"));
    values.add(calibratedFloat(RLR_BEHAVIOR_THRESHOLD, "RLR behavioral-similarity threshold",
        "Callable pairs at or above this static behavioral similarity are redundancy candidates.", "0.90"));
    values.add(csv(RLR_BEHAVIOR_COMPONENT_WEIGHTS, "RLR behavioral component weights",
        "Behavior-context,call-set,return-output weights; must sum to one.",
        "0.333333333333,0.333333333333,0.333333333334"));
    values.add(calibratedFloat(SII_CONCEPT_THRESHOLD, "SII concept-similarity threshold",
        "Minimum normalized identifier-concept similarity for a naming-consistency candidate.", "0.75"));
    values.add(calibratedFloat(SII_CONTEXT_THRESHOLD, "SII usage-context threshold",
        "Minimum AST usage-context similarity for a naming-consistency candidate.", "0.75"));
    values.add(calibratedFloat(SII_NAME_THRESHOLD, "SII name-similarity ceiling",
        "Candidates must have normalized lexical name similarity below this ceiling.", "0.40"));
    values.add(calibratedInteger(EGR_COMPLEXITY_THRESHOLD, "EGR complexity threshold",
        "Minimum callable cyclomatic complexity for complexity-based selection.", "5"));
    values.add(calibratedInteger(EGR_NESTING_THRESHOLD, "EGR nesting threshold",
        "Minimum maximum nesting depth for nesting-based selection.", "3"));
    values.add(calibratedInteger(EGR_MIXED_FLOW_COMPLEXITY_THRESHOLD, "EGR mixed-flow complexity threshold",
        "Minimum cyclomatic complexity for the combined control-flow selection criterion.", "4"));
    values.add(calibratedInteger(EGR_MIXED_FLOW_KIND_THRESHOLD, "EGR control-flow-family threshold",
        "Minimum distinct control-flow families for the combined selection criterion.", "2"));
    values.add(PropertyDefinition.builder(PAIR_BUDGET).name("Pairwise-analysis budget")
        .description("Maximum callable or identifier pairs analyzed by each pairwise metric. "
            + "The round-robin traversal represents every source region before repeating an item; "
            + "reaching the budget is reported in evidence.")
        .category("AI Debt").type(PropertyType.INTEGER).defaultValue("1000000").build());
    values.add(PropertyDefinition.builder(SPECDETECT_REPORT).name("SpecDetect4AI report path")
        .description("Path relative to the scanner base directory of the SpecDetect4AI JSON report used as the AISD evidence source.")
        .category("AI Debt").type(PropertyType.STRING).defaultValue("specDetect4ai_results.json").build());
    values.add(PropertyDefinition.builder(CALIBRATION_EXPORT_PATH).name("Threshold-calibration export path")
        .description("Optional JSONL output path for all pre-threshold CSD, RLR, SII, and EGR candidates. "
            + "Leave blank during ordinary scans; enable only when building a blinded calibration dataset.")
        .category("AI Debt").type(PropertyType.STRING).build());
    values.add(PropertyDefinition.builder(CALIBRATION_GROUP_ID).name("Threshold-calibration group ID")
        .description("Independent project or task-family identifier used for grouped validation. "
            + "It must not encode whether the source is human- or AI-produced.")
        .category("AI Debt").type(PropertyType.STRING).build());
    return List.copyOf(values);
  }

  private static PropertyDefinition csv(String key, String name, String description, String defaultValue) {
    return PropertyDefinition.builder(key).name(name).description(description).category("AI Debt")
        .type(PropertyType.STRING).defaultValue(defaultValue).build();
  }

  private static PropertyDefinition calibratedFloat(
      String key, String name, String description, String defaultValue) {
    return PropertyDefinition.builder(key).name(name)
        .description(description + " Frozen in the consensus-adjudicated-v1 profile.")
        .category("AI Debt").type(PropertyType.FLOAT).defaultValue(defaultValue).build();
  }

  private static PropertyDefinition calibratedInteger(
      String key, String name, String description, String defaultValue) {
    return PropertyDefinition.builder(key).name(name)
        .description(description + " Frozen in the consensus-adjudicated-v1 profile.")
        .category("AI Debt").type(PropertyType.INTEGER).defaultValue(defaultValue).build();
  }
}
