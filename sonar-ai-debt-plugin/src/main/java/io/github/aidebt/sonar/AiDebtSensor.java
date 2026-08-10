package io.github.aidebt.sonar;

import io.github.aidebt.core.AnalysisConfig;
import io.github.aidebt.core.AnalysisResult;
import io.github.aidebt.core.CalibrationCollector;
import io.github.aidebt.core.DebtFinding;
import io.github.aidebt.core.MetricKey;
import io.github.aidebt.core.ProjectAnalyzer;
import io.github.aidebt.core.SourceUnit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.measures.Metric;
import org.sonar.api.issue.impact.SoftwareQuality;
import org.sonar.api.issue.impact.Severity;
import org.sonar.api.rules.CleanCodeAttribute;
import org.sonar.api.rules.RuleType;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public final class AiDebtSensor implements Sensor {
  private static final Logger LOG = Loggers.get(AiDebtSensor.class);
  private static final List<DiagnosticBinding> DIAGNOSTICS = List.of(
      bind(AiDebtMetrics.FILES, "project.files"), bind(AiDebtMetrics.LOGICAL_LINES, "project.logical_lines"),
      bind(AiDebtMetrics.BLOCKS, "project.blocks"), bind(AiDebtMetrics.AISD_SMELLS, "aisd.smells"),
      bind(AiDebtMetrics.AISD_KLOC, "aisd.kloc"), bind(AiDebtMetrics.SMELL_BROAD_EXCEPT, "aisd.smell.broad-except"),
      bind(AiDebtMetrics.SMELL_MUTABLE_DEFAULT, "aisd.smell.mutable-default"),
      bind(AiDebtMetrics.SMELL_WILDCARD_IMPORT, "aisd.smell.wildcard-import"),
      bind(AiDebtMetrics.SMELL_DEBUG_OUTPUT, "aisd.smell.debug-output"),
      bind(AiDebtMetrics.SMELL_UNSAFE_EVAL, "aisd.smell.unsafe-eval"),
      bind(AiDebtMetrics.SMELL_PLACEHOLDER, "aisd.smell.placeholder"),
      bind(AiDebtMetrics.SMELL_SWALLOWED_EXCEPTION, "aisd.smell.swallowed-exception"),
      bind(AiDebtMetrics.SMELL_EVALUATION_LEAKAGE, "aisd.smell.training-on-evaluation-data"),
      bind(AiDebtMetrics.SMELL_HARDCODED_SECRET, "aisd.smell.hardcoded-secret"),
      bind(AiDebtMetrics.CII_CA, "cii.ca"), bind(AiDebtMetrics.CII_CE, "cii.ce"),
      bind(AiDebtMetrics.CII_COUPLED_FILES, "cii.coupled_files"),
      bind(AiDebtMetrics.CII_INTERNAL, "cii.internal_dependencies"),
      bind(AiDebtMetrics.CII_EXTERNAL, "cii.external_dependencies"),
      bind(AiDebtMetrics.CII_CYCLES, "cii.cycles"),
      bind(AiDebtMetrics.CII_STABILITY_VIOLATIONS, "cii.stability_violations"),
      bind(AiDebtMetrics.CII_REMEDIATION_ACTIONS, "cii.remediation_actions"),
      bind(AiDebtMetrics.CDI_MEAN_COMPLEXITY, "cdi.mean_complexity"),
      bind(AiDebtMetrics.CDI_COMMENT_DENSITY, "cdi.comment_density"),
      bind(AiDebtMetrics.CDI_COMMENT_LINES, "cdi.comment_lines"),
      bind(AiDebtMetrics.CDI_SOURCE_LINES, "cdi.source_lines"),
      bind(AiDebtMetrics.CDI_BLOCKS, "cdi.blocks"),
      bind(AiDebtMetrics.CDI_MEAN_NESTING, "cdi.mean_nesting"),
      bind(AiDebtMetrics.CDI_DOCUMENTED_BLOCKS, "cdi.documented_blocks"),
      bind(AiDebtMetrics.CDI_DOCUMENTATION_COVERAGE, "cdi.documentation_coverage"),
      bind(AiDebtMetrics.CDI_TOTAL_COMPLEXITY_EXCESS, "cdi.total_complexity_excess"),
      bind(AiDebtMetrics.CDI_UNDOCUMENTED_COMPLEXITY_EXCESS, "cdi.undocumented_complexity_excess"),
      bind(AiDebtMetrics.HTS_IMPLICIT, "htd.implicit"), bind(AiDebtMetrics.HTS_EXPLICIT, "htd.explicit"),
      bind(AiDebtMetrics.HTS_CONFIG_DRIVEN, "htd.config_driven"), bind(AiDebtMetrics.HTS_TOTAL, "htd.total"),
      bind(AiDebtMetrics.HTS_OPAQUE_CONFIG, "htd.opaque_config"),
      bind(AiDebtMetrics.HTS_MISSING_SEED, "htd.missing_seed"),
      bind(AiDebtMetrics.HTS_UNPINNED_REVISION, "htd.unpinned_revision"),
      bind(AiDebtMetrics.CSD_SWITCHES, "csd.switches"), bind(AiDebtMetrics.CSD_TRANSITIONS, "csd.transitions"),
      bind(AiDebtMetrics.CSD_MEAN_SIMILARITY, "csd.mean_similarity"),
      bind(AiDebtMetrics.CSD_MINIMUM_SIMILARITY, "csd.minimum_similarity"),
      bind(AiDebtMetrics.CSD_THRESHOLD, "csd.threshold"),
      bind(AiDebtMetrics.RLR_REDUNDANT, "rlr.redundant"), bind(AiDebtMetrics.RLR_PAIRS, "rlr.pairs"),
      bind(AiDebtMetrics.RLR_MAX_SYNTACTIC, "rlr.maximum_syntactic_similarity"),
      bind(AiDebtMetrics.RLR_MAX_SEMANTIC, "rlr.maximum_semantic_similarity"),
      bind(AiDebtMetrics.RLR_SYNTACTIC_THRESHOLD, "rlr.syntactic_threshold"),
      bind(AiDebtMetrics.RLR_SEMANTIC_THRESHOLD, "rlr.semantic_threshold"),
      bind(AiDebtMetrics.SII_INCONSISTENT, "sii.inconsistent"), bind(AiDebtMetrics.SII_PAIRS, "sii.pairs"),
      bind(AiDebtMetrics.SII_IDENTIFIERS, "sii.identifiers"),
      bind(AiDebtMetrics.SII_SEMANTICALLY_SIMILAR, "sii.semantically_similar"),
      bind(AiDebtMetrics.SII_MAX_SEMANTIC, "sii.maximum_semantic_similarity"),
      bind(AiDebtMetrics.SII_MAX_CONTEXT, "sii.maximum_context_similarity"),
      bind(AiDebtMetrics.SII_MIN_LEXICAL, "sii.minimum_lexical_among_similar"),
      bind(AiDebtMetrics.SII_SEMANTIC_THRESHOLD, "sii.semantic_threshold"),
      bind(AiDebtMetrics.SII_CONTEXT_THRESHOLD, "sii.context_threshold"),
      bind(AiDebtMetrics.SII_LEXICAL_THRESHOLD, "sii.lexical_threshold"),
      bind(AiDebtMetrics.EGR_UNEXPLAINED, "egr.unexplained"), bind(AiDebtMetrics.EGR_COMPLEX, "egr.complex"),
      bind(AiDebtMetrics.EGR_CC_TRIGGERED, "egr.cc_triggered"),
      bind(AiDebtMetrics.EGR_NESTING_TRIGGERED, "egr.nesting_triggered"),
      bind(AiDebtMetrics.EGR_CONTROL_FLOW_TRIGGERED, "egr.control_flow_triggered"),
      bind(AiDebtMetrics.EGR_CC_THRESHOLD, "egr.cc_threshold"),
      bind(AiDebtMetrics.EGR_NESTING_THRESHOLD, "egr.nesting_threshold"),
      bind(AiDebtMetrics.W_AISD, "weight.aisd"), bind(AiDebtMetrics.W_CII, "weight.cii"),
      bind(AiDebtMetrics.W_CDI, "weight.cdi"), bind(AiDebtMetrics.W_HTS, "weight.hts"),
      bind(AiDebtMetrics.W_CSD, "weight.csd"), bind(AiDebtMetrics.W_RLR, "weight.rlr"),
      bind(AiDebtMetrics.W_SII, "weight.sii"), bind(AiDebtMetrics.W_EGR, "weight.egr"),
      bind(AiDebtMetrics.W_TDSI, "weight.tdsi"), bind(AiDebtMetrics.W_COGDI, "weight.cogdi"));

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor.name("AI technical and cognitive debt sensor")
        .onlyOnLanguage("py")
        .onlyOnFileType(Type.MAIN);
  }

  @Override
  public void execute(SensorContext context) {
    if (!context.config().getBoolean(AiDebtProperties.ENABLED).orElse(true)) return;
    List<SourceUnit> sources = new ArrayList<>();
    Map<String, InputFile> inputByPath = new HashMap<>();
    Iterable<InputFile> files = context.fileSystem().inputFiles(context.fileSystem().predicates().hasType(Type.MAIN));
    for (InputFile input : files) {
      if (!SourceUnit.supports(input.filename())) continue;
      try {
        String path = input.uri().getPath();
        sources.add(new SourceUnit(path, input.contents(), SourceUnit.languageOf(input.filename())));
        inputByPath.put(path, input);
      } catch (IOException | RuntimeException error) {
        LOG.warn("AI Debt skipped {}: {}", input, error.getMessage());
      }
    }
    if (sources.isEmpty()) {
      LOG.info("AI Debt found no supported main files");
      return;
    }

    AnalysisConfig config;
    try {
      config = configuration(context);
    } catch (IllegalArgumentException error) {
      throw new IllegalStateException("Invalid AI Debt configuration: " + error.getMessage(), error);
    }
    Path baseDirectory = context.fileSystem().baseDir().toPath().toAbsolutePath().normalize();
    CalibrationCollector calibration = calibrationCollector(context);
    ProjectAnalyzer analyzer = new ProjectAnalyzer(config, calibration);
    AnalysisResult result;
    String configuredReport = context.config().get(AiDebtProperties.SPECDETECT_REPORT)
        .orElse("specDetect4ai_results.json");
    Path report = Path.of(configuredReport);
    if (!report.isAbsolute()) report = baseDirectory.resolve(report);
    report = report.normalize();
    if (Files.isRegularFile(report)) {
      try {
        List<DebtFinding> specDetectFindings = new SpecDetectReportParser().parse(report, baseDirectory);
        result = analyzer.analyzeWithSpecDetect(sources, specDetectFindings);
        LOG.info("AI Debt loaded {} SpecDetect4AI findings from {}", specDetectFindings.size(), report);
      } catch (IOException error) {
        throw new IllegalStateException("Cannot read SpecDetect4AI report " + report + ": " + error.getMessage(), error);
      }
    } else {
      LOG.warn("AI Debt AISD is N/A because the SpecDetect4AI report was not found at {}", report);
      result = analyzer.analyzeWithoutSpecDetect(sources);
    }
    exportCalibration(context, baseDirectory, calibration);
    if (result.metric(MetricKey.AISD).applicable()) {
      save(context, AiDebtMetrics.AISD, result.metric(MetricKey.AISD).raw());
      save(context, AiDebtMetrics.AISD_SCORE, result.metric(MetricKey.AISD).normalized());
    }
    saveIfApplicable(context, AiDebtMetrics.CII, result, MetricKey.CII);
    save(context, AiDebtMetrics.CDI, result.metric(MetricKey.CDI).raw());
    save(context, AiDebtMetrics.CDI_SCORE, result.metric(MetricKey.CDI).normalized());
    saveIfApplicable(context, AiDebtMetrics.HTS, result, MetricKey.HTS);
    saveIfApplicable(context, AiDebtMetrics.CSD, result, MetricKey.CSD);
    saveIfApplicable(context, AiDebtMetrics.RLR, result, MetricKey.RLR);
    saveIfApplicable(context, AiDebtMetrics.SII, result, MetricKey.SII);
    saveIfApplicable(context, AiDebtMetrics.EGR, result, MetricKey.EGR);
    save(context, AiDebtMetrics.TDSI, result.tdsi());
    save(context, AiDebtMetrics.COGDI, result.cogdi());
    save(context, AiDebtMetrics.ADSI, result.adsi());
    save(context, AiDebtMetrics.COVERAGE, result.coverage() * 100.0);
    for (DiagnosticBinding binding : DIAGNOSTICS) {
      save(context, binding.metric(), result.diagnostic(binding.key()));
    }
    saveData(context, AiDebtMetrics.CII_EVIDENCE, result.artifact("cii.evidence"));
    saveData(context, AiDebtMetrics.CDI_EVIDENCE, result.artifact("cdi.evidence"));
    saveData(context, AiDebtMetrics.HTS_EVIDENCE, result.artifact("hts.evidence"));
    saveData(context, AiDebtMetrics.CSD_EVIDENCE, result.artifact("csd.evidence"));
    saveData(context, AiDebtMetrics.RLR_EVIDENCE, result.artifact("rlr.evidence"));
    saveData(context, AiDebtMetrics.SII_EVIDENCE, result.artifact("sii.evidence"));
    saveData(context, AiDebtMetrics.EGR_EVIDENCE, result.artifact("egr.evidence"));
    saveData(context, AiDebtMetrics.EFFORT_MODEL, result.artifact("effort.model"));
    publishIssues(context, result, inputByPath);
    LOG.info("AI Debt analyzed {} files / {} logical lines; ADSI={}", result.files(), result.lines(), round(result.adsi()));
  }

  private static CalibrationCollector calibrationCollector(SensorContext context) {
    String exportPath = context.config().get(AiDebtProperties.CALIBRATION_EXPORT_PATH).orElse("").strip();
    if (exportPath.isEmpty()) return CalibrationCollector.disabled();
    String groupId = context.config().get(AiDebtProperties.CALIBRATION_GROUP_ID)
        .filter(value -> !value.isBlank())
        .orElse(context.project().key());
    return CalibrationCollector.enabled(groupId);
  }

  private static void exportCalibration(SensorContext context, Path baseDirectory,
                                        CalibrationCollector calibration) {
    if (!calibration.enabled()) return;
    Path output = Path.of(context.config().get(AiDebtProperties.CALIBRATION_EXPORT_PATH).orElseThrow());
    if (!output.isAbsolute()) output = baseDirectory.resolve(output);
    output = output.normalize();
    try {
      Path parent = output.getParent();
      if (parent != null) Files.createDirectories(parent);
      Files.writeString(output, calibration.jsonLines(), StandardCharsets.UTF_8);
      LOG.info("AI Debt exported {} pre-threshold calibration candidates to {}", calibration.size(), output);
    } catch (IOException error) {
      throw new IllegalStateException("Cannot write AI Debt calibration export " + output + ": "
          + error.getMessage(), error);
    }
  }

  private static AnalysisConfig configuration(SensorContext context) {
    AnalysisConfig defaults = AnalysisConfig.defaults();
    double[] td = weights(context.config().get(AiDebtProperties.TDSI_WEIGHTS).orElse("0.25,0.25,0.25,0.25"), 4);
    double[] cognitive = weights(context.config().get(AiDebtProperties.COGDI_WEIGHTS).orElse("0.25,0.25,0.25,0.25"), 4);
    double[] index = weights(context.config().get(AiDebtProperties.INDEX_WEIGHTS).orElse("0.5,0.5"), 2);
    EnumMap<MetricKey, Double> values = new EnumMap<>(MetricKey.class);
    MetricKey[] keys = MetricKey.values();
    for (int i = 0; i < 4; i++) values.put(keys[i], td[i]);
    for (int i = 0; i < 4; i++) values.put(keys[i + 4], cognitive[i]);
    double csdThreshold = context.config().getDouble(AiDebtProperties.CSD_SIMILARITY_THRESHOLD)
        .orElse(defaults.contextSimilarityThreshold());
    double[] csdComponents = weights(context.config().get(AiDebtProperties.CSD_COMPONENT_WEIGHTS)
        .orElse("0.333333333333,0.333333333333,0.333333333334"), 3);
    double rlrSyntaxThreshold = context.config().getDouble(AiDebtProperties.RLR_SYNTAX_THRESHOLD)
        .orElse(defaults.syntacticRedundancyThreshold());
    double rlrBehaviorThreshold = context.config().getDouble(AiDebtProperties.RLR_BEHAVIOR_THRESHOLD)
        .orElse(defaults.semanticRedundancyThreshold());
    double[] rlrBehaviorComponents = weights(context.config()
        .get(AiDebtProperties.RLR_BEHAVIOR_COMPONENT_WEIGHTS)
        .orElse("0.333333333333,0.333333333333,0.333333333334"), 3);
    double siiConceptThreshold = context.config().getDouble(AiDebtProperties.SII_CONCEPT_THRESHOLD)
        .orElse(defaults.semanticConsistencyThreshold());
    double siiContextThreshold = context.config().getDouble(AiDebtProperties.SII_CONTEXT_THRESHOLD)
        .orElse(defaults.semanticContextThreshold());
    double siiNameThreshold = context.config().getDouble(AiDebtProperties.SII_NAME_THRESHOLD)
        .orElse(defaults.lexicalConsistencyThreshold());
    int egrComplexityThreshold = context.config().getInt(AiDebtProperties.EGR_COMPLEXITY_THRESHOLD)
        .orElse(defaults.complexBlockThreshold());
    int egrNestingThreshold = context.config().getInt(AiDebtProperties.EGR_NESTING_THRESHOLD)
        .orElse(defaults.complexNestingThreshold());
    int egrMixedFlowComplexityThreshold = context.config()
        .getInt(AiDebtProperties.EGR_MIXED_FLOW_COMPLEXITY_THRESHOLD)
        .orElse(defaults.mixedFlowComplexityThreshold());
    int egrMixedFlowKindThreshold = context.config().getInt(AiDebtProperties.EGR_MIXED_FLOW_KIND_THRESHOLD)
        .orElse(defaults.mixedFlowKindThreshold());
    int pairBudget = context.config().getInt(AiDebtProperties.PAIR_BUDGET)
        .orElse(defaults.pairBudget());
    return new AnalysisConfig(values, index[0], index[1], defaults.aisdScale(),
        csdThreshold, csdComponents[0], csdComponents[1], csdComponents[2],
        rlrSyntaxThreshold, rlrBehaviorThreshold,
        rlrBehaviorComponents[0], rlrBehaviorComponents[1], rlrBehaviorComponents[2],
        siiConceptThreshold,
        siiContextThreshold, siiNameThreshold, egrComplexityThreshold, egrNestingThreshold,
        egrMixedFlowComplexityThreshold, egrMixedFlowKindThreshold, pairBudget);
  }

  private static double[] weights(String csv, int expected) {
    String[] parts = csv.split(",");
    if (parts.length != expected) throw new IllegalArgumentException("Expected " + expected + " comma-separated weights: " + csv);
    double[] result = new double[expected];
    double sum = 0;
    for (int i = 0; i < expected; i++) { result[i] = Double.parseDouble(parts[i].trim()); sum += result[i]; }
    if (Math.abs(sum - 1.0) > 1e-9) throw new IllegalArgumentException("Weights must sum to one: " + csv);
    return result;
  }

  private static void saveIfApplicable(SensorContext context, Metric<Double> metric, AnalysisResult result, MetricKey key) {
    if (result.metric(key).applicable()) save(context, metric, result.metric(key).normalized());
  }

  private static void save(SensorContext context, Metric<Double> metric, double value) {
    context.<Double>newMeasure().forMetric(metric).on(context.project()).withValue(round(value)).save();
  }

  private static void saveData(SensorContext context, Metric<String> metric, String value) {
    if (value == null || value.isBlank()) return;
    context.<String>newMeasure().forMetric(metric).on(context.project()).withValue(value).save();
  }

  private static void publishIssues(SensorContext context, AnalysisResult result, Map<String, InputFile> inputByPath) {
    for (DebtFinding finding : result.findings()) {
      InputFile input = inputByPath.get(finding.file());
      if (input == null || finding.line() < 1 || finding.line() > input.lines()
          || !AiDebtRules.RULES.containsKey(finding.rule())) continue;
      var issue = context.newExternalIssue()
          .engineId("AI Debt Python")
          .ruleId(finding.specification().id())
          .type(RuleType.CODE_SMELL)
          .cleanCodeAttribute(CleanCodeAttribute.COMPLETE)
          .severity(batchSeverity(finding.specification().severity()))
          .addImpact(SoftwareQuality.MAINTAINABILITY, Severity.valueOf(finding.specification().severity()));
      var location = issue.newLocation().on(input).message(finding.message());
      if (finding.endLine() > finding.line()) {
        int end = Math.min(finding.endLine(), input.lines());
        location.at(input.newRange(finding.line(), 0, end, 0));
      } else {
        location.at(input.selectLine(finding.line()));
      }
      issue.at(location).save();
    }
  }

  private static org.sonar.api.batch.rule.Severity batchSeverity(String severity) {
    return switch (severity) {
      case "HIGH" -> org.sonar.api.batch.rule.Severity.CRITICAL;
      case "MEDIUM" -> org.sonar.api.batch.rule.Severity.MAJOR;
      default -> org.sonar.api.batch.rule.Severity.MINOR;
    };
  }

  private static double round(double value) { return Math.round(value * 10_000.0) / 10_000.0; }

  private static DiagnosticBinding bind(Metric<Double> metric, String key) {
    return new DiagnosticBinding(metric, key);
  }

  private record DiagnosticBinding(Metric<Double> metric, String key) {}
}
