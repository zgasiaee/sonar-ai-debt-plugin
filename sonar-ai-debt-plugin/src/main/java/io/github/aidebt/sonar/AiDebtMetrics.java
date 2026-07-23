package io.github.aidebt.sonar;

import java.util.List;
import org.sonar.api.measures.Metric;
import org.sonar.api.measures.Metrics;

public final class AiDebtMetrics implements Metrics {
  public static final String DOMAIN = "AI Debt";

  public static final Metric<Double> AISD = metric("aidebt_aisd", "AISD (raw)", "AI-specific smell instances per KLOC");
  public static final Metric<Double> AISD_SCORE = metric("aidebt_aisd_score", "AISD debt score", "Normalized AISD in [0, 1]");
  public static final Metric<Double> CII = metric("aidebt_cii", "CII", "Mean coupling instability over coupled files");
  public static final Metric<Double> CDI = metric("aidebt_cdi", "CDI", "Share of decision complexity belonging to undocumented callable blocks");
  public static final Metric<Double> CDI_SCORE = metric("aidebt_cdi_score", "CDI debt score", "Normalized CDI in [0, 1]");
  public static final Metric<Double> HTS = benefitMetric("aidebt_hts", "Hyperparameter transparency score", "Model initializations with explicit or resolvable configuration divided by all model initializations");
  public static final Metric<Double> CSD = metric("aidebt_csd", "CSD", "Low style–structure similarity transitions divided by same-scope adjacent callable transitions");
  public static final Metric<Double> RLR = metric("aidebt_rlr", "RLR", "Redundant function pairs divided by analyzed function pairs");
  public static final Metric<Double> SII = metric("aidebt_sii", "SII", "High-behavior-similarity, low-name-similarity pairs divided by identifier pairs");
  public static final Metric<Double> EGR = metric("aidebt_egr", "EGR", "Complex blocks without rationale divided by complex blocks");
  public static final Metric<Double> TDSI = metric("aidebt_tdsi", "TDSI", "Normalized Technical Debt Severity Index");
  public static final Metric<Double> COGDI = metric("aidebt_cogdi", "CogDI", "Normalized Cognitive Debt Index");
  public static final Metric<Double> ADSI = metric("aidebt_adsi", "ADSI", "Aggregated AI-code Debt Severity Index");
  public static final Metric<Double> COVERAGE = percentage("aidebt_metric_coverage", "Metric applicability", "Share of configured metric weight that was applicable");

  // Analysis scope diagnostics
  public static final Metric<Double> FILES = diagnostic("aidebt_files", "Analyzed files", "Supported main-source files analyzed");
  public static final Metric<Double> LOGICAL_LINES = diagnostic("aidebt_logical_lines", "Logical lines", "Nonblank logical code lines analyzed");
  public static final Metric<Double> BLOCKS = diagnostic("aidebt_blocks", "Analyzed blocks", "Functions or module blocks analyzed");

  // Metric numerators, denominators, and intermediate values
  public static final Metric<Double> AISD_SMELLS = diagnostic("aidebt_aisd_smells", "AISD smell instances", "Unique AI-associated smell instances");
  public static final Metric<Double> AISD_KLOC = diagnostic("aidebt_aisd_kloc", "AISD KLOC", "Actual analyzed KLOC denominator");
  public static final Metric<Double> SMELL_BROAD_EXCEPT = diagnostic("aidebt_smell_broad_except", "Broad except smells", "Broad Python exception handlers");
  public static final Metric<Double> SMELL_MUTABLE_DEFAULT = diagnostic("aidebt_smell_mutable_default", "Mutable default smells", "Mutable Python default arguments");
  public static final Metric<Double> SMELL_WILDCARD_IMPORT = diagnostic("aidebt_smell_wildcard_import", "Wildcard import smells", "Python wildcard imports");
  public static final Metric<Double> SMELL_DEBUG_OUTPUT = diagnostic("aidebt_smell_debug_output", "Debug output smells", "Python print calls left in production code");
  public static final Metric<Double> SMELL_UNSAFE_EVAL = diagnostic("aidebt_smell_unsafe_eval", "Unsafe evaluation smells", "Dynamic eval or exec calls");
  public static final Metric<Double> SMELL_PLACEHOLDER = diagnostic("aidebt_smell_placeholder", "Placeholder smells", "Incomplete placeholder implementations");
  public static final Metric<Double> SMELL_SWALLOWED_EXCEPTION = diagnostic("aidebt_smell_swallowed_exception", "Swallowed exceptions", "Python exception handlers that contain only pass");
  public static final Metric<Double> SMELL_EVALUATION_LEAKAGE = diagnostic("aidebt_smell_evaluation_leakage", "Evaluation leakage candidates", "Fit operations that appear to consume test or validation data");
  public static final Metric<Double> SMELL_HARDCODED_SECRET = diagnostic("aidebt_smell_hardcoded_secret", "Hard-coded secret smells", "Potential hard-coded secrets");
  public static final Metric<Double> CII_CA = diagnostic("aidebt_cii_ca", "CII afferent coupling", "Total incoming internal dependencies used in CII diagnostics");
  public static final Metric<Double> CII_CE = diagnostic("aidebt_cii_ce", "CII efferent coupling", "Total outgoing dependencies used in CII diagnostics");
  public static final Metric<Double> CII_COUPLED_FILES = diagnostic("aidebt_cii_coupled_files", "CII coupled files", "Files with at least one incoming or outgoing dependency");
  public static final Metric<Double> CII_INTERNAL = diagnostic("aidebt_cii_internal_dependencies", "Internal dependencies", "Resolved dependencies between analyzed Python modules");
  public static final Metric<Double> CII_EXTERNAL = diagnostic("aidebt_cii_external_dependencies", "External dependencies", "Imports not resolved to an analyzed Python module");
  public static final Metric<String> CII_EVIDENCE = data("aidebt_cii_evidence", "CII dependency evidence", "Per-file coupling and source-located import evidence as JSON");
  public static final Metric<Double> CDI_MEAN_COMPLEXITY = diagnostic("aidebt_cdi_mean_complexity", "CDI mean complexity", "Mean cyclomatic complexity of analyzed blocks");
  public static final Metric<Double> CDI_COMMENT_DENSITY = diagnostic("aidebt_cdi_comment_density", "CDI comment density", "Comment lines divided by source lines");
  public static final Metric<Double> CDI_COMMENT_LINES = diagnostic("aidebt_cdi_comment_lines", "CDI comment lines", "Comment-line numerator");
  public static final Metric<Double> CDI_SOURCE_LINES = diagnostic("aidebt_cdi_source_lines", "CDI source lines", "Code plus comment-line denominator");
  public static final Metric<Double> CDI_MEAN_NESTING = diagnostic("aidebt_cdi_mean_nesting", "Mean nesting", "Mean maximum AST control-flow nesting over functions");
  public static final Metric<Double> CDI_DOCUMENTED_BLOCKS = diagnostic("aidebt_cdi_documented_blocks", "Documented blocks", "Functions with a docstring or associated comments");
  public static final Metric<Double> CDI_DOCUMENTATION_COVERAGE = diagnostic("aidebt_cdi_documentation_coverage", "Documentation coverage", "Share of functions with documentation evidence");
  public static final Metric<Double> CDI_TOTAL_COMPLEXITY_EXCESS = diagnostic("aidebt_cdi_total_complexity_excess", "Total decision complexity", "Sum of cyclomatic complexity minus its baseline over callable blocks");
  public static final Metric<Double> CDI_UNDOCUMENTED_COMPLEXITY_EXCESS = diagnostic("aidebt_cdi_undocumented_complexity_excess", "Undocumented decision complexity", "Decision complexity belonging to undocumented callable blocks");
  public static final Metric<String> CDI_EVIDENCE = data("aidebt_cdi_evidence", "CDI block evidence", "Per-block complexity and documentation evidence as JSON");
  public static final Metric<Double> HTS_IMPLICIT = diagnostic("aidebt_hts_implicit", "Implicit ML initializations", "ML initializations without explicit hyperparameters");
  public static final Metric<Double> HTS_EXPLICIT = diagnostic("aidebt_hts_explicit", "Explicit ML initializations", "ML initializations with positional or keyword configuration");
  public static final Metric<Double> HTS_CONFIG_DRIVEN = diagnostic("aidebt_hts_config_driven", "Config-driven ML initializations", "ML initializations configured through a resolvable **kwargs expansion");
  public static final Metric<Double> HTS_TOTAL = diagnostic("aidebt_hts_total", "Total ML initializations", "All detected ML initializations used as the HTS denominator");
  public static final Metric<Double> HTS_OPAQUE_CONFIG = diagnostic("aidebt_hts_opaque_config", "Opaque ML configurations", "ML **configuration expansions whose keys cannot be resolved");
  public static final Metric<Double> HTS_MISSING_SEED = diagnostic("aidebt_hts_missing_seed", "Missing random seeds", "Stochastic ML initializations without a seed parameter");
  public static final Metric<Double> HTS_UNPINNED_REVISION = diagnostic("aidebt_hts_unpinned_revision", "Unpinned model revisions", "Pretrained model loads without a revision parameter");
  public static final Metric<String> HTS_EVIDENCE = data("aidebt_hts_evidence", "HTS initialization evidence", "Every detected ML initialization with source location and classification as JSON");
  public static final Metric<String> CSD_EVIDENCE = data("aidebt_csd_evidence", "CSD switch evidence", "Detected style–structure switches with both source ranges and similarity components as compact JSON");
  public static final Metric<String> RLR_EVIDENCE = data("aidebt_rlr_evidence", "RLR pair evidence", "Detected redundant callable pairs with both source ranges and syntactic and semantic-proxy similarities as JSON");
  public static final Metric<Double> CSD_SWITCHES = diagnostic("aidebt_csd_switches", "Context switches", "Low style–structure similarity transitions");
  public static final Metric<Double> CSD_TRANSITIONS = diagnostic("aidebt_csd_transitions", "Block transitions", "Total same-scope adjacent callable transitions");
  public static final Metric<Double> CSD_MEAN_SIMILARITY = diagnostic("aidebt_csd_mean_similarity", "Mean context similarity", "Mean style–structure similarity of adjacent callables");
  public static final Metric<Double> CSD_MINIMUM_SIMILARITY = diagnostic("aidebt_csd_minimum_similarity", "Minimum context similarity", "Lowest style–structure similarity of adjacent callables");
  public static final Metric<Double> CSD_THRESHOLD = diagnostic("aidebt_csd_threshold", "CSD threshold", "Configured context-switch similarity threshold");
  public static final Metric<Double> RLR_REDUNDANT = diagnostic("aidebt_rlr_redundant", "Redundant pairs", "Redundant function pairs");
  public static final Metric<Double> RLR_PAIRS = diagnostic("aidebt_rlr_pairs", "Function pairs", "Function pairs analyzed for redundancy");
  public static final Metric<Double> RLR_MAX_SYNTACTIC = diagnostic("aidebt_rlr_maximum_syntactic_similarity", "Maximum syntax similarity", "Highest normalized shingle similarity among analyzed function pairs");
  public static final Metric<Double> RLR_MAX_SEMANTIC = diagnostic("aidebt_rlr_maximum_semantic_similarity", "Maximum semantic similarity", "Highest structural/call/output similarity among analyzed function pairs");
  public static final Metric<Double> RLR_SYNTACTIC_THRESHOLD = diagnostic("aidebt_rlr_syntactic_threshold", "RLR syntax threshold", "Configured syntactic redundancy threshold");
  public static final Metric<Double> RLR_SEMANTIC_THRESHOLD = diagnostic("aidebt_rlr_semantic_threshold", "RLR semantic threshold", "Configured semantic redundancy threshold");
  public static final Metric<Double> SII_INCONSISTENT = diagnostic("aidebt_sii_inconsistent", "Inconsistent pairs", "High-behavior/low-name-similarity pairs");
  public static final Metric<Double> SII_PAIRS = diagnostic("aidebt_sii_pairs", "Identifier pairs", "Same-kind non-RLR identifier pairs analyzed");
  public static final Metric<Double> SII_IDENTIFIERS = diagnostic("aidebt_sii_identifiers", "Contextual identifiers", "Functions, assigned variables, and classes with an AST-context embedding");
  public static final Metric<Double> SII_SEMANTICALLY_SIMILAR = diagnostic("aidebt_sii_semantically_similar", "Semantically similar pairs", "Pairs crossing the SII semantic threshold");
  public static final Metric<Double> SII_MAX_SEMANTIC = diagnostic("aidebt_sii_maximum_semantic_similarity", "Maximum SII semantic similarity", "Highest SII semantic similarity among analyzed pairs");
  public static final Metric<Double> SII_MAX_CONTEXT = diagnostic("aidebt_sii_maximum_context_similarity", "Maximum SII context similarity", "Highest AST-context similarity among analyzed same-kind identifier pairs");
  public static final Metric<Double> SII_MIN_LEXICAL = diagnostic("aidebt_sii_minimum_lexical_among_similar", "Minimum intent similarity", "Lowest intent similarity among semantically similar pairs");
  public static final Metric<Double> SII_SEMANTIC_THRESHOLD = diagnostic("aidebt_sii_semantic_threshold", "SII semantic threshold", "Configured semantic similarity threshold");
  public static final Metric<Double> SII_CONTEXT_THRESHOLD = diagnostic("aidebt_sii_context_threshold", "SII context threshold", "Configured minimum AST-context similarity for comparable identifier concepts");
  public static final Metric<Double> SII_LEXICAL_THRESHOLD = diagnostic("aidebt_sii_lexical_threshold", "SII lexical threshold", "Configured low intent-similarity threshold");
  public static final Metric<String> SII_EVIDENCE = data("aidebt_sii_evidence", "SII pair evidence", "High-context-semantic, low-Levenshtein identifier pairs with both source ranges as JSON");
  public static final Metric<Double> EGR_UNEXPLAINED = diagnostic("aidebt_egr_unexplained", "Unexplained complex blocks", "Complex blocks without rationale");
  public static final Metric<Double> EGR_COMPLEX = diagnostic("aidebt_egr_complex", "Complex blocks", "Blocks meeting at least one complexity, nesting, or control-flow criterion");
  public static final Metric<Double> EGR_CC_TRIGGERED = diagnostic("aidebt_egr_cc_triggered", "Cyclomatic complexity-triggered blocks", "Complex blocks crossing the cyclomatic-complexity threshold");
  public static final Metric<Double> EGR_NESTING_TRIGGERED = diagnostic("aidebt_egr_nesting_triggered", "Nesting-triggered blocks", "Complex blocks crossing the maximum-nesting threshold");
  public static final Metric<Double> EGR_CONTROL_FLOW_TRIGGERED = diagnostic("aidebt_egr_control_flow_triggered", "Control-flow-triggered blocks", "Complex blocks crossing the mixed-control-flow criterion");
  public static final Metric<Double> EGR_CC_THRESHOLD = diagnostic("aidebt_egr_cc_threshold", "EGR CC threshold", "Configured cyclomatic-complexity threshold");
  public static final Metric<Double> EGR_NESTING_THRESHOLD = diagnostic("aidebt_egr_nesting_threshold", "EGR nesting threshold", "Maximum-nesting threshold");
  public static final Metric<String> EGR_EVIDENCE = data("aidebt_egr_evidence", "EGR block evidence", "Current-analysis complex blocks, triggers, rationale status, and source ranges as JSON");

  // Configured weights are published to make each score reproducible from the dashboard.
  public static final Metric<Double> W_AISD = diagnostic("aidebt_weight_aisd", "AISD weight", "Configured AISD weight");
  public static final Metric<Double> W_CII = diagnostic("aidebt_weight_cii", "CII weight", "Configured CII weight");
  public static final Metric<Double> W_CDI = diagnostic("aidebt_weight_cdi", "CDI weight", "Configured CDI weight");
  public static final Metric<Double> W_HTS = diagnostic("aidebt_weight_hts", "HTS weight", "Configured HTS weight; aggregation uses 1 minus HTS");
  public static final Metric<Double> W_CSD = diagnostic("aidebt_weight_csd", "CSD weight", "Configured CSD weight");
  public static final Metric<Double> W_RLR = diagnostic("aidebt_weight_rlr", "RLR weight", "Configured RLR weight");
  public static final Metric<Double> W_SII = diagnostic("aidebt_weight_sii", "SII weight", "Configured SII weight");
  public static final Metric<Double> W_EGR = diagnostic("aidebt_weight_egr", "EGR weight", "Configured EGR weight");
  public static final Metric<Double> W_TDSI = diagnostic("aidebt_weight_tdsi", "TDSI aggregate weight", "Configured higher-order TDSI weight");
  public static final Metric<Double> W_COGDI = diagnostic("aidebt_weight_cogdi", "CogDI aggregate weight", "Configured higher-order CogDI weight");

  public static final List<Metric> ALL = List.of(
      AISD, AISD_SCORE, CII, CDI, CDI_SCORE, HTS, CSD, RLR, SII, EGR, TDSI, COGDI, ADSI, COVERAGE,
      FILES, LOGICAL_LINES, BLOCKS, AISD_SMELLS, AISD_KLOC,
      SMELL_BROAD_EXCEPT, SMELL_MUTABLE_DEFAULT, SMELL_WILDCARD_IMPORT, SMELL_DEBUG_OUTPUT,
      SMELL_UNSAFE_EVAL, SMELL_PLACEHOLDER, SMELL_SWALLOWED_EXCEPTION, SMELL_EVALUATION_LEAKAGE,
      SMELL_HARDCODED_SECRET,
      CII_CA, CII_CE, CII_COUPLED_FILES, CII_INTERNAL, CII_EXTERNAL, CII_EVIDENCE,
      CDI_MEAN_COMPLEXITY, CDI_COMMENT_DENSITY, CDI_COMMENT_LINES, CDI_SOURCE_LINES,
      CDI_MEAN_NESTING, CDI_DOCUMENTED_BLOCKS, CDI_DOCUMENTATION_COVERAGE, CDI_EVIDENCE,
      CDI_TOTAL_COMPLEXITY_EXCESS, CDI_UNDOCUMENTED_COMPLEXITY_EXCESS,
      HTS_IMPLICIT, HTS_EXPLICIT, HTS_CONFIG_DRIVEN, HTS_TOTAL, HTS_OPAQUE_CONFIG,
      HTS_MISSING_SEED, HTS_UNPINNED_REVISION, HTS_EVIDENCE, CSD_EVIDENCE, RLR_EVIDENCE,
      CSD_SWITCHES, CSD_TRANSITIONS, CSD_MEAN_SIMILARITY, CSD_MINIMUM_SIMILARITY, CSD_THRESHOLD,
      RLR_REDUNDANT, RLR_PAIRS, RLR_MAX_SYNTACTIC, RLR_MAX_SEMANTIC, RLR_SYNTACTIC_THRESHOLD,
      RLR_SEMANTIC_THRESHOLD, SII_INCONSISTENT, SII_PAIRS, SII_IDENTIFIERS, SII_SEMANTICALLY_SIMILAR,
      SII_MAX_SEMANTIC, SII_MAX_CONTEXT, SII_MIN_LEXICAL, SII_SEMANTIC_THRESHOLD,
      SII_CONTEXT_THRESHOLD, SII_LEXICAL_THRESHOLD,
      SII_EVIDENCE,
      EGR_UNEXPLAINED, EGR_COMPLEX, EGR_CC_TRIGGERED, EGR_NESTING_TRIGGERED,
      EGR_CONTROL_FLOW_TRIGGERED, EGR_CC_THRESHOLD, EGR_NESTING_THRESHOLD, EGR_EVIDENCE,
      W_AISD, W_CII, W_CDI, W_HTS, W_CSD, W_RLR, W_SII, W_EGR, W_TDSI, W_COGDI);

  @Override
  public List<Metric> getMetrics() {
    return List.copyOf(ALL);
  }

  private static Metric<Double> metric(String key, String name, String description) {
    return new Metric.Builder(key, name, Metric.ValueType.FLOAT)
        .setDescription(description).setDomain(DOMAIN).setDirection(Metric.DIRECTION_WORST)
        .setQualitative(false).setDecimalScale(4).create();
  }

  private static Metric<Double> benefitMetric(String key, String name, String description) {
    return new Metric.Builder(key, name, Metric.ValueType.FLOAT)
        .setDescription(description).setDomain(DOMAIN).setDirection(Metric.DIRECTION_BETTER)
        .setQualitative(false).setDecimalScale(4).create();
  }

  private static Metric<Double> percentage(String key, String name, String description) {
    return new Metric.Builder(key, name, Metric.ValueType.PERCENT)
        .setDescription(description).setDomain(DOMAIN).setDirection(Metric.DIRECTION_BETTER)
        .setQualitative(false).setDecimalScale(1).create();
  }

  private static Metric<Double> diagnostic(String key, String name, String description) {
    return new Metric.Builder(key, name, Metric.ValueType.FLOAT)
        .setDescription(description).setDomain(DOMAIN).setDirection(Metric.DIRECTION_NONE)
        .setQualitative(false).setDecimalScale(4).create();
  }

  private static Metric<String> data(String key, String name, String description) {
    return new Metric.Builder(key, name, Metric.ValueType.DATA)
        .setDescription(description).setDomain(DOMAIN).setDirection(Metric.DIRECTION_NONE)
        .setQualitative(false).create();
  }
}
