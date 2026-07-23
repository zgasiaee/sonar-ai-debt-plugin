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
  public static final String SPECDETECT_REPORT = PREFIX + "specdetect.reportPath";

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
            + "The default is provisional and should be frozen after calibration on labeled transitions.")
        .category("AI Debt").type(PropertyType.FLOAT).defaultValue("0.45").build());
    values.add(PropertyDefinition.builder(SPECDETECT_REPORT).name("SpecDetect4AI report path")
        .description("Path relative to the scanner base directory of the SpecDetect4AI JSON report used as the AISD evidence source.")
        .category("AI Debt").type(PropertyType.STRING).defaultValue("specDetect4ai_results.json").build());
    return List.copyOf(values);
  }

  private static PropertyDefinition csv(String key, String name, String description, String defaultValue) {
    return PropertyDefinition.builder(key).name(name).description(description).category("AI Debt")
        .type(PropertyType.STRING).defaultValue(defaultValue).build();
  }
}
