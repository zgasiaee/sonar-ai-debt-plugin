package io.github.aidebt.core;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects pre-threshold metric candidates for blinded threshold calibration.
 *
 * <p>The collector is disabled during ordinary analysis. When enabled, it records both positive
 * and negative candidates so that a later calibration run is not biased by the current engineering
 * defaults.
 */
public final class CalibrationCollector {
  public static final String SCHEMA = "aidebt-threshold-calibration-v1";
  private static final Gson GSON = new Gson();
  private final String groupId;
  private final String unitId;
  private final List<Map<String, Object>> candidates = new ArrayList<>();
  private final Map<String, String> sources = new LinkedHashMap<>();

  private CalibrationCollector(String groupId, String unitId) {
    this.groupId = groupId;
    this.unitId = unitId;
  }

  public static CalibrationCollector disabled() {
    return new CalibrationCollector(null, null);
  }

  public static CalibrationCollector enabled(String groupId) {
    return enabled(groupId, groupId);
  }

  public static CalibrationCollector enabled(String groupId, String unitId) {
    if (groupId == null || groupId.isBlank()) {
      throw new IllegalArgumentException("A non-blank calibration group ID is required");
    }
    if (unitId == null || unitId.isBlank()) {
      throw new IllegalArgumentException("A non-blank calibration unit ID is required");
    }
    return new CalibrationCollector(groupId.strip(), unitId.strip());
  }

  public boolean enabled() {
    return groupId != null;
  }

  public int size() {
    return candidates.size();
  }

  void attachSources(List<SourceUnit> units) {
    if (!enabled()) return;
    for (SourceUnit unit : units) sources.put(unit.path(), unit.content());
  }

  void recordCsd(CodeBlock previous, CodeBlock current, double similarity, double naming,
                 double patterns, double structure, double threshold, boolean predicted) {
    if (!enabled()) return;
    Map<String, Object> record = base("CSD", locationKey(previous) + "|" + locationKey(current));
    record.put("file", previous.file());
    record.put("scope", previous.scope());
    record.put("from_name", previous.name());
    record.put("from_start_line", previous.startLine());
    record.put("from_end_line", previous.endLine());
    record.put("to_name", current.name());
    record.put("to_start_line", current.startLine());
    record.put("to_end_line", current.endLine());
    record.put("similarity", similarity);
    record.put("naming_similarity", naming);
    record.put("pattern_similarity", patterns);
    record.put("structure_similarity", structure);
    record.put("current_threshold", threshold);
    record.put("current_prediction", predicted);
    record.put("from_source", snippet(previous.file(), previous.startLine(), previous.endLine()));
    record.put("to_source", snippet(current.file(), current.startLine(), current.endLine()));
    candidates.add(record);
  }

  void recordRlr(CodeBlock first, CodeBlock second, double syntax,
                 ProjectAnalyzer.RlrBehaviorComparison behavior,
                 boolean hasBehavior, double syntaxThreshold, double behaviorThreshold,
                 boolean predicted) {
    if (!enabled()) return;
    Map<String, Object> record = base("RLR", locationKey(first) + "|" + locationKey(second));
    addPair(record, first, second);
    record.put("syntax_similarity", syntax);
    record.put("behavior_similarity", behavior.overall());
    record.put("behavior_context_similarity", behavior.behaviorContext());
    record.put("call_similarity", behavior.calls());
    record.put("output_similarity", behavior.outputs());
    record.put("behavior_available", hasBehavior);
    record.put("current_syntax_threshold", syntaxThreshold);
    record.put("current_behavior_threshold", behaviorThreshold);
    record.put("current_prediction", predicted);
    candidates.add(record);
  }

  void recordSii(IdentifierProfile first, IdentifierProfile second, double concept, double context,
                 double lexical, boolean excludedByRlr, double conceptThreshold,
                 double contextThreshold, double lexicalCeiling, boolean predicted) {
    if (!enabled()) return;
    Map<String, Object> record = base("SII", identifierKey(first) + "|" + identifierKey(second));
    record.put("kind", first.kind());
    record.put("first_file", first.file());
    record.put("first_scope", first.scope());
    record.put("first_name", first.name());
    record.put("first_start_line", first.startLine());
    record.put("first_end_line", first.endLine());
    record.put("second_file", second.file());
    record.put("second_scope", second.scope());
    record.put("second_name", second.name());
    record.put("second_start_line", second.startLine());
    record.put("second_end_line", second.endLine());
    record.put("concept_similarity", concept);
    record.put("context_similarity", context);
    record.put("lexical_similarity", lexical);
    record.put("excluded_by_rlr", excludedByRlr);
    record.put("current_concept_threshold", conceptThreshold);
    record.put("current_context_threshold", contextThreshold);
    record.put("current_lexical_ceiling", lexicalCeiling);
    record.put("current_prediction", predicted);
    record.put("first_source", snippet(first.file(), first.startLine(), first.endLine()));
    record.put("second_source", snippet(second.file(), second.startLine(), second.endLine()));
    candidates.add(record);
  }

  void recordEgr(CodeBlock block, boolean selected, boolean ccTriggered, boolean nestingTriggered,
                 boolean mixedFlowTriggered, List<String> flowKinds, boolean hasRationale,
                 String rationaleSource, int ccThreshold, int nestingThreshold,
                 int mixedCcThreshold, int flowKindThreshold) {
    if (!enabled()) return;
    Map<String, Object> record = base("EGR", locationKey(block));
    record.put("file", block.file());
    record.put("name", block.name());
    record.put("scope", block.scope());
    record.put("start_line", block.startLine());
    record.put("end_line", block.endLine());
    record.put("cyclomatic_complexity", block.complexity());
    record.put("maximum_nesting", block.maxNesting());
    record.put("flow_family_count", flowKinds.size());
    record.put("flow_families", flowKinds);
    record.put("has_rationale", hasRationale);
    record.put("rationale_source", rationaleSource);
    record.put("current_cc_threshold", ccThreshold);
    record.put("current_nesting_threshold", nestingThreshold);
    record.put("current_mixed_cc_threshold", mixedCcThreshold);
    record.put("current_flow_kind_threshold", flowKindThreshold);
    record.put("current_cc_trigger", ccTriggered);
    record.put("current_nesting_trigger", nestingTriggered);
    record.put("current_mixed_flow_trigger", mixedFlowTriggered);
    record.put("current_prediction", selected);
    record.put("source", snippet(block.file(), block.startLine(), block.endLine()));
    candidates.add(record);
  }

  public String jsonLines() {
    if (!enabled()) return "";
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("record_type", "metadata");
    metadata.put("schema", SCHEMA);
    metadata.put("group_id", groupId);
    metadata.put("unit_id", unitId);
    metadata.put("candidate_count", candidates.size());
    StringBuilder output = new StringBuilder(GSON.toJson(metadata)).append('\n');
    for (Map<String, Object> candidate : candidates) {
      output.append(GSON.toJson(candidate)).append('\n');
    }
    return output.toString();
  }

  private Map<String, Object> base(String metric, String identity) {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("record_type", "candidate");
    record.put("schema", SCHEMA);
    record.put("group_id", groupId);
    record.put("unit_id", unitId);
    record.put("metric", metric);
    record.put("candidate_id", metric.toLowerCase() + "-" + digest(unitId + "|" + identity));
    return record;
  }

  private void addPair(Map<String, Object> record, CodeBlock first, CodeBlock second) {
    record.put("first_file", first.file());
    record.put("first_name", first.name());
    record.put("first_start_line", first.startLine());
    record.put("first_end_line", first.endLine());
    record.put("second_file", second.file());
    record.put("second_name", second.name());
    record.put("second_start_line", second.startLine());
    record.put("second_end_line", second.endLine());
    record.put("first_source", snippet(first.file(), first.startLine(), first.endLine()));
    record.put("second_source", snippet(second.file(), second.startLine(), second.endLine()));
  }

  private String snippet(String file, int startLine, int endLine) {
    String content = sources.get(file);
    if (content == null || content.isBlank()) return "";
    String[] lines = content.split("\\R", -1);
    int start = Math.max(1, startLine);
    int end = Math.min(lines.length, Math.max(start, endLine));
    if (start > lines.length) return "";
    return String.join("\n", java.util.Arrays.copyOfRange(lines, start - 1, end));
  }

  private static String locationKey(CodeBlock block) {
    return block.file() + "|" + block.scope() + "|" + block.name() + "|" + block.startLine();
  }

  private static String identifierKey(IdentifierProfile identifier) {
    return identifier.file() + "|" + identifier.scope() + "|" + identifier.kind() + "|"
        + identifier.name() + "|" + identifier.startLine();
  }

  private static String digest(String value) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash, 0, 8);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }
}
