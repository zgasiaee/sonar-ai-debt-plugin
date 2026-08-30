package io.github.aidebt.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Computes all eight metrics from the same collection-of-files abstraction. */
public final class ProjectAnalyzer {
  private static final int MAX_RLR_EVIDENCE_PAIRS = 25;
  private static final int MAX_SII_EVIDENCE_PAIRS = 25;
  private static final int MAX_EGR_EVIDENCE_BLOCKS = 100;
  private static final Set<String> RATIONALE_TERMS = Set.of(
      "because", "since", "therefore", "in order to", "so that", "to avoid", "to prevent",
      "reason", "rationale", "purpose", "why", "edge case", "special case", "correctness",
      "safety", "performance", "fallback", "guard", "retry", "validation", "invariant");
  private static final Map<String, String> IDENTIFIER_CONCEPTS = identifierConcepts();
  private final AnalysisConfig config;
  private final CalibrationCollector calibration;
  private final RemediationEffortPolicy effortPolicy;
  private final PythonAstParser parser = new PythonAstParser();
  private final PythonMlRegistry mlRegistry = new PythonMlRegistry();

  public ProjectAnalyzer(AnalysisConfig config) {
    this(config, CalibrationCollector.disabled(), RemediationEffortPolicy.defaults());
  }

  public ProjectAnalyzer(AnalysisConfig config, CalibrationCollector calibration) {
    this(config, calibration, RemediationEffortPolicy.defaults());
  }

  public ProjectAnalyzer(AnalysisConfig config, CalibrationCollector calibration,
                         RemediationEffortPolicy effortPolicy) {
    this.config = config;
    this.calibration = calibration;
    this.effortPolicy = effortPolicy;
  }

  public AnalysisResult analyze(List<SourceUnit> sources) {
    return analyze(sources, null, true);
  }

  /** Uses SpecDetect4AI findings as the exclusive AISD numerator. */
  public AnalysisResult analyzeWithSpecDetect(List<SourceUnit> sources, List<DebtFinding> specDetectFindings) {
    return analyze(sources, List.copyOf(specDetectFindings), false);
  }

  /** Keeps AISD not-applicable when the configured external evidence report is unavailable. */
  public AnalysisResult analyzeWithoutSpecDetect(List<SourceUnit> sources) {
    return analyze(sources, null, false);
  }

  private AnalysisResult analyze(List<SourceUnit> sources, List<DebtFinding> specDetectFindings,
                                 boolean useLegacyAisdForCoreTests) {
    calibration.attachSources(sources);
    List<ParsedUnit> units = sources.stream().filter(s -> SourceUnit.supports(s.path())).map(parser::parse).toList();
    List<CodeBlock> blocks = units.stream().flatMap(u -> u.blocks().stream()).toList();
    Map<String, Double> diagnostics = new HashMap<>();
    Map<String, String> artifacts = new HashMap<>();
    List<DebtFinding> findings = sourceFindings(units);
    List<RemediationEffortModel.Action> remediationActions = new ArrayList<>();
    diagnostics.put("project.files", (double) units.size());
    diagnostics.put("project.blocks", (double) blocks.size());
    EnumMap<MetricKey, MetricValue> metrics = new EnumMap<>(MetricKey.class);
    metrics.put(MetricKey.HTS, hyperparameterTransparency(units, findings, diagnostics, artifacts, remediationActions));
    List<DebtFinding> aisdEvidence = useLegacyAisdForCoreTests ? findings : specDetectFindings;
    if (specDetectFindings != null) findings.addAll(specDetectFindings);
    metrics.put(MetricKey.AISD, aisd(units, aisdEvidence, diagnostics, remediationActions));
    metrics.put(MetricKey.CII, cii(units, findings, diagnostics, artifacts, remediationActions));
    metrics.put(MetricKey.CDI, cdi(units, blocks, diagnostics, artifacts, remediationActions));
    metrics.put(MetricKey.CSD, csd(units, findings, diagnostics, artifacts, remediationActions));
    metrics.put(MetricKey.RLR, rlr(blocks, findings, diagnostics, artifacts, remediationActions));
    metrics.put(MetricKey.SII, sii(units, findings, diagnostics, artifacts, remediationActions));
    metrics.put(MetricKey.EGR, egr(blocks, findings, diagnostics, artifacts, remediationActions));
    int lines = units.stream().mapToInt(ParsedUnit::logicalLines).sum();
    diagnostics.put("project.logical_lines", (double) lines);
    for (MetricKey key : MetricKey.values()) diagnostics.put("weight." + key.name().toLowerCase(Locale.ROOT), config.weights().get(key));
    diagnostics.put("weight.tdsi", config.tdsiWeight());
    diagnostics.put("weight.cogdi", config.cogdiWeight());
    artifacts.put("effort.model", RemediationEffortModel.report(remediationActions, metrics, effortPolicy));
    return new AnalysisResult(metrics, ScoreAggregator.aggregate(metrics, config), units.size(), lines, diagnostics, findings, artifacts);
  }

  private static List<DebtFinding> sourceFindings(List<ParsedUnit> units) {
    List<DebtFinding> result = new ArrayList<>();
    for (ParsedUnit unit : units) {
      for (PythonFinding finding : unit.findings()) {
        result.add(new DebtFinding(unit.source().path(), finding.line(), finding.rule(), finding.evidence()));
      }
      Matcher matcher = Pattern.compile("(?im)^(?!\\s*#).*\\b(?:password|passwd|secret|api[_-]?key|token)\\b\\s*[:=]\\s*['\"][^'\"]{6,}")
          .matcher(unit.source().content());
      while (matcher.find()) {
        int line = 1;
        for (int i = 0; i < matcher.start(); i++) if (unit.source().content().charAt(i) == '\n') line++;
        result.add(new DebtFinding(unit.source().path(), line, "hardcoded-secret", "Potential credential literal"));
      }
    }
    return result;
  }

  private MetricValue aisd(List<ParsedUnit> units, List<DebtFinding> detailedFindings,
                           Map<String, Double> diagnostics,
                           List<RemediationEffortModel.Action> remediationActions) {
    Set<String> findings = new LinkedHashSet<>();
    int lines = 0;
    for (ParsedUnit unit : units) {
      lines += unit.logicalLines();
    }
    diagnostics.put("aisd.kloc", lines / 1000.0);
    if (detailedFindings == null) {
      diagnostics.put("aisd.smells", 0.0);
      diagnostics.put("aisd.specdetect_report_available", 0.0);
      return MetricValue.notApplicable();
    }
    diagnostics.put("aisd.specdetect_report_available", 1.0);
    for (DebtFinding finding : detailedFindings) {
      String occurrence = finding.file() + ':' + finding.line() + ':' + finding.rule();
      if (findings.add(occurrence)) {
        remediationActions.add(RemediationEffortModel.action(MetricKey.AISD,
            aisdRemediationKey(finding), aisdEffortTier(finding.rule())));
      }
    }
    Map<String, Integer> byRule = new HashMap<>();
    for (String finding : findings) byRule.merge(finding.substring(finding.lastIndexOf(':') + 1), 1, Integer::sum);
    byRule.forEach((rule, count) -> diagnostics.put("aisd.smell." + rule, count.doubleValue()));
    for (String rule : List.of("broad-except", "mutable-default", "wildcard-import", "debug-output",
        "unsafe-eval", "placeholder", "swallowed-exception", "training-on-evaluation-data",
        "hardcoded-secret", "opaque-ml-config", "missing-random-seed", "unpinned-model-revision",
        "multiply-nested-container", "long-parameter-list", "long-method", "long-lambda", "long-ternary",
        "complex-comprehension", "long-message-chain", "large-class")) {
      diagnostics.put("aisd.smell." + rule, byRule.getOrDefault(rule, 0).doubleValue());
    }
    diagnostics.put("aisd.smells", (double) findings.size());
    if (lines == 0) return MetricValue.notApplicable();
    double raw = findings.size() / (lines / 1000.0);
    double normalized = 1.0 - Math.exp(-raw / config.aisdScale());
    return MetricValue.applicable(raw, normalized, findings.size(), lines);
  }

  private MetricValue cii(List<ParsedUnit> units, List<DebtFinding> findings,
                          Map<String, Double> diagnostics, Map<String, String> artifacts,
                          List<RemediationEffortModel.Action> remediationActions) {
    if (units.isEmpty()) return MetricValue.notApplicable();
    Map<String, Integer> incoming = new HashMap<>();
    Map<String, Integer> outgoing = new HashMap<>();
    long internalDependencies = 0, externalDependencies = 0;
    List<String> dependencyJson = new ArrayList<>();
    List<DependencyEdge> internalEdges = new ArrayList<>();
    for (ParsedUnit unit : units) { incoming.put(unit.source().path(), 0); outgoing.put(unit.source().path(), unit.imports().size()); }
    for (ParsedUnit source : units) {
      for (String imported : source.imports()) {
        String target = resolveImport(source.source().path(), imported, units);
        if (target != null && !target.equals(source.source().path())) {
          int line = source.importLines().getOrDefault(imported, 1);
          incoming.merge(target, 1, Integer::sum);
          internalDependencies++;
          internalEdges.add(new DependencyEdge(source.source().path(), line, imported, target));
          dependencyJson.add(dependencyJson(source.source().path(), line, imported, "internal", target));
        } else if (target == null) {
          externalDependencies++;
          dependencyJson.add(dependencyJson(source.source().path(), source.importLines().getOrDefault(imported, 1),
              imported, "external", null));
        }
      }
    }
    double sum = 0;
    int coupled = 0;
    long ce = 0, total = 0;
    List<String> fileJson = new ArrayList<>();
    Map<String, Double> fileInstability = new HashMap<>();
    for (ParsedUnit unit : units) {
      int ca = incoming.get(unit.source().path());
      int out = outgoing.get(unit.source().path());
      if (ca + out == 0) continue;
      double fileCii = (double) out / (ca + out);
      fileInstability.put(unit.source().path(), fileCii);
      sum += fileCii;
      ce += out;
      total += ca + out;
      coupled++;
      fileJson.add("{\"file\":\"" + json(unit.source().path()) + "\",\"ca\":" + ca
          + ",\"ce\":" + out + ",\"cii\":" + format(fileCii) + "}");
    }
    List<Set<String>> cycles = stronglyConnectedComponents(units, internalEdges).stream()
        .filter(component -> component.size() > 1).toList();
    Map<String, Integer> cycleByFile = new HashMap<>();
    List<String> cycleJson = new ArrayList<>();
    for (int i = 0; i < cycles.size(); i++) {
      int cycleId = i + 1;
      Set<String> component = cycles.get(i);
      List<String> modules = new ArrayList<>(component);
      Collections.sort(modules);
      modules.forEach(module -> cycleByFile.put(module, cycleId));
      List<DependencyEdge> cycleEdges = internalEdges.stream()
          .filter(edge -> component.contains(edge.source()) && component.contains(edge.target()))
          .sorted(Comparator.comparing(DependencyEdge::source).thenComparingInt(DependencyEdge::line)
              .thenComparing(DependencyEdge::target)).toList();
      if (!cycleEdges.isEmpty()) {
        DependencyEdge representative = cycleEdges.get(0);
        String route = String.join(" -> ", modules) + " -> " + modules.get(0);
        findings.add(new DebtFinding(representative.source(), representative.line(), "coupling-cycle",
            "Internal dependency cycle: " + route));
        remediationActions.add(RemediationEffortModel.action(MetricKey.CII,
            "coupling-cycle:" + String.join("|", modules), RemediationEffortModel.Tier.MAJOR));
      }
      cycleJson.add("{\"id\":" + cycleId + ",\"modules\":" + jsonArray(modules)
          + ",\"edges\":[" + String.join(",", cycleEdges.stream().map(ProjectAnalyzer::edgeJson).toList()) + "]}");
    }

    List<String> violationJson = new ArrayList<>();
    for (DependencyEdge edge : internalEdges) {
      if (cycleByFile.getOrDefault(edge.source(), -1).equals(cycleByFile.getOrDefault(edge.target(), -2))) continue;
      double sourceInstability = fileInstability.getOrDefault(edge.source(), 0.0);
      double targetInstability = fileInstability.getOrDefault(edge.target(), 0.0);
      if (targetInstability <= sourceInstability + 1e-9) continue;
      findings.add(new DebtFinding(edge.source(), edge.line(), "unstable-dependency-direction",
          "Dependency points from instability " + format(sourceInstability) + " to less stable module "
              + edge.target() + " (" + format(targetInstability) + ")"));
      remediationActions.add(RemediationEffortModel.action(MetricKey.CII,
          "unstable-dependency:" + edge.source() + ':' + edge.line() + "->" + edge.target(),
          RemediationEffortModel.Tier.MODERATE));
      violationJson.add("{\"source\":\"" + json(edge.source()) + "\",\"line\":" + edge.line()
          + ",\"module\":\"" + json(edge.module()) + "\",\"target\":\"" + json(edge.target())
          + "\",\"sourceInstability\":" + format(sourceInstability)
          + ",\"targetInstability\":" + format(targetInstability) + "}");
    }
    artifacts.put("cii.evidence", "{\"files\":[" + String.join(",", fileJson)
        + "],\"dependencies\":[" + String.join(",", dependencyJson) + "],\"cycles\":["
        + String.join(",", cycleJson) + "],\"stabilityViolations\":["
        + String.join(",", violationJson) + "]}");
    diagnostics.put("cii.ce", (double) ce);
    diagnostics.put("cii.ca", (double) (total - ce));
    diagnostics.put("cii.coupled_files", (double) coupled);
    diagnostics.put("cii.internal_dependencies", (double) internalDependencies);
    diagnostics.put("cii.external_dependencies", (double) externalDependencies);
    diagnostics.put("cii.cycles", (double) cycles.size());
    diagnostics.put("cii.stability_violations", (double) violationJson.size());
    diagnostics.put("cii.remediation_actions", (double) (cycles.size() + violationJson.size()));
    if (coupled == 0) return MetricValue.notApplicable();
    double raw = sum / coupled;
    return MetricValue.applicable(raw, raw, ce, total);
  }

  private MetricValue cdi(List<ParsedUnit> units, List<CodeBlock> blocks, Map<String, Double> diagnostics,
                          Map<String, String> artifacts,
                          List<RemediationEffortModel.Action> remediationActions) {
    if (units.isEmpty() || blocks.isEmpty()) return MetricValue.notApplicable();
    int logical = units.stream().mapToInt(ParsedUnit::logicalLines).sum();
    int comments = units.stream().mapToInt(ParsedUnit::commentLines).sum();
    int sourceLines = logical + comments;
    double density = sourceLines == 0 ? 0 : (double) comments / sourceLines;
    double meanComplexity = blocks.stream().mapToInt(CodeBlock::complexity).average().orElse(1.0);
    double meanNesting = blocks.stream().mapToInt(CodeBlock::maxNesting).average().orElse(0.0);
    long documentedBlocks = blocks.stream()
        .filter(block -> !block.docstring().isBlank() || !block.nearbyComments().isBlank()).count();
    double documentationCoverage = (double) documentedBlocks / blocks.size();
    long totalComplexityExcess = blocks.stream().mapToLong(block -> Math.max(0, block.complexity() - 1)).sum();
    long undocumentedComplexityExcess = blocks.stream()
        .filter(block -> block.docstring().isBlank() && block.nearbyComments().isBlank())
        .mapToLong(block -> Math.max(0, block.complexity() - 1)).sum();
    double raw = totalComplexityExcess == 0 ? 0.0
        : (double) undocumentedComplexityExcess / totalComplexityExcess;
    double normalized = raw;
    diagnostics.put("cdi.mean_complexity", meanComplexity);
    diagnostics.put("cdi.comment_density", density);
    diagnostics.put("cdi.comment_lines", (double) comments);
    diagnostics.put("cdi.source_lines", (double) sourceLines);
    diagnostics.put("cdi.blocks", (double) blocks.size());
    diagnostics.put("cdi.mean_nesting", meanNesting);
    diagnostics.put("cdi.documented_blocks", (double) documentedBlocks);
    diagnostics.put("cdi.documentation_coverage", documentationCoverage);
    diagnostics.put("cdi.total_complexity_excess", (double) totalComplexityExcess);
    diagnostics.put("cdi.undocumented_complexity_excess", (double) undocumentedComplexityExcess);
    List<String> blockJson = new ArrayList<>();
    for (CodeBlock block : blocks) {
      boolean documented = !block.docstring().isBlank() || !block.nearbyComments().isBlank();
      int complexityExcess = Math.max(0, block.complexity() - 1);
      if (!documented && complexityExcess > 0) {
        remediationActions.add(RemediationEffortModel.action(MetricKey.CDI,
            documentationActionKey(block), complexityEffortTier(block)));
      }
      double debtContribution = !documented && totalComplexityExcess > 0
          ? (double) complexityExcess / totalComplexityExcess : 0.0;
      String source = !block.docstring().isBlank() ? "docstring" : !block.nearbyComments().isBlank() ? "comment" : "none";
      blockJson.add("{\"file\":\"" + json(block.file()) + "\",\"name\":\"" + json(block.name())
          + "\",\"startLine\":" + block.startLine() + ",\"endLine\":" + block.endLine()
          + ",\"complexity\":" + block.complexity() + ",\"nesting\":" + block.maxNesting()
          + ",\"commentLines\":" + block.nearbyCommentLines()
          + ",\"complexityExcess\":" + complexityExcess + ",\"debtContribution\":" + format(debtContribution)
          + ",\"documented\":" + documented + ",\"documentationSource\":\"" + source + "\"}");
    }
    artifacts.put("cdi.evidence", "{\"logicalLines\":" + logical + ",\"commentLines\":" + comments
        + ",\"sourceLines\":" + sourceLines + ",\"commentDensity\":" + format(density)
        + ",\"meanComplexity\":" + format(meanComplexity) + ",\"totalComplexityExcess\":"
        + totalComplexityExcess + ",\"undocumentedComplexityExcess\":" + undocumentedComplexityExcess
        + ",\"meanNesting\":" + format(meanNesting)
        + ",\"documentedBlocks\":" + documentedBlocks + ",\"totalBlocks\":" + blocks.size()
        + ",\"blocks\":[" + String.join(",", blockJson) + "]}");
    return MetricValue.applicable(raw, normalized, comments, sourceLines);
  }

  private MetricValue hyperparameterTransparency(List<ParsedUnit> units, List<DebtFinding> findings,
                                         Map<String, Double> diagnostics, Map<String, String> artifacts,
                                         List<RemediationEffortModel.Action> remediationActions) {
    long total = 0, implicit = 0;
    long configDriven = 0, explicit = 0, opaqueConfig = 0, missingSeed = 0, unpinnedRevision = 0;
    Map<String, Integer> frameworks = new HashMap<>();
    List<String> initializationJson = new ArrayList<>();
    for (ParsedUnit unit : units) {
      for (PythonCall call : unit.calls()) {
        if (!mlRegistry.isInitialization(call)) continue;
        total++;
        frameworks.merge(mlRegistry.framework(call), 1, Integer::sum);
        String category;
        if (!call.hasExplicitConfiguration()) { implicit++; category = "implicit"; }
        else if (call.unresolvedConfigurationExpansion() && !call.hasDirectArguments()) {
          opaqueConfig++; category = "opaque";
        } else if (call.configurationExpansion()) { configDriven++; category = "config-driven"; }
        else { explicit++; category = "explicit"; }
        if (category.equals("implicit") || category.equals("opaque")) {
          remediationActions.add(RemediationEffortModel.action(MetricKey.HTS,
              mlConfigurationActionKey(unit.source().path(), call.line()),
              category.equals("opaque") ? RemediationEffortModel.Tier.MAJOR
                  : RemediationEffortModel.Tier.MODERATE));
        }
        if (call.unresolvedConfigurationExpansion()) {
          findings.add(new DebtFinding(unit.source().path(), call.line(), "opaque-ml-config",
              "The expanded ML configuration cannot be resolved to explicit keys"));
        }
        if (mlRegistry.requiresSeed(call) && !mlRegistry.hasSeed(call)) {
          missingSeed++;
          findings.add(new DebtFinding(unit.source().path(), call.line(), "missing-random-seed",
              "A stochastic ML component has no explicit random seed"));
        }
        if (mlRegistry.requiresPinnedRevision(call) && !mlRegistry.hasPinnedRevision(call)) {
          unpinnedRevision++;
          findings.add(new DebtFinding(unit.source().path(), call.line(), "unpinned-model-revision",
              "A pretrained model is loaded without a pinned revision"));
        }
        boolean missingRandomSeed = mlRegistry.requiresSeed(call) && !mlRegistry.hasSeed(call);
        boolean missingRevisionPin = mlRegistry.requiresPinnedRevision(call) && !mlRegistry.hasPinnedRevision(call);
        initializationJson.add("{\"file\":\"" + json(unit.source().path()) + "\",\"line\":" + call.line()
            + ",\"constructor\":\"" + json(call.qualifiedName()) + "\",\"framework\":\""
            + json(mlRegistry.framework(call)) + "\",\"category\":\"" + category
            + "\",\"positionalArguments\":" + call.positionalArguments().size()
            + ",\"keywordArguments\":" + jsonArray(call.keywordArguments().keySet())
            + ",\"resolvedConfigKeys\":" + jsonArray(call.resolvedConfigurationKeys())
            + ",\"configExpansions\":" + jsonArray(call.configurationExpansions())
            + ",\"missingSeed\":" + missingRandomSeed + ",\"unpinnedRevision\":" + missingRevisionPin + "}");
      }
    }
    diagnostics.put("htd.implicit", (double) implicit);
    diagnostics.put("htd.total", (double) total);
    diagnostics.put("htd.explicit", (double) explicit);
    diagnostics.put("htd.config_driven", (double) configDriven);
    diagnostics.put("htd.opaque_config", (double) opaqueConfig);
    diagnostics.put("htd.missing_seed", (double) missingSeed);
    diagnostics.put("htd.unpinned_revision", (double) unpinnedRevision);
    frameworks.forEach((framework, count) -> diagnostics.put("htd.framework." + framework, count.doubleValue()));
    artifacts.put("hts.evidence", "{\"initializations\":[" + String.join(",", initializationJson) + "]}");
    if (total == 0) return MetricValue.notApplicable();
    long transparent = explicit + configDriven;
    double hts = (double) transparent / total;
    return MetricValue.applicable(hts, hts, transparent, total);
  }

  private MetricValue csd(List<ParsedUnit> units, List<DebtFinding> findings, Map<String, Double> diagnostics,
                          Map<String, String> artifacts,
                          List<RemediationEffortModel.Action> remediationActions) {
    long switches = 0, transitions = 0;
    double similaritySum = 0.0;
    double minimumSimilarity = 1.0;
    List<String> transitionJson = new ArrayList<>();
    for (ParsedUnit unit : units) {
      Map<String, List<CodeBlock>> scopes = new HashMap<>();
      unit.blocks().stream().sorted(Comparator.comparingInt(CodeBlock::startLine))
          .forEach(block -> scopes.computeIfAbsent(block.scope(), ignored -> new ArrayList<>()).add(block));
      for (String scope : scopes.keySet().stream().sorted().toList()) {
        List<CodeBlock> blocks = scopes.get(scope);
        for (int i = 0; i + 1 < blocks.size(); i++) {
          CodeBlock previous = blocks.get(i), current = blocks.get(i + 1);
          ContextComparison comparison = compareContext(previous, current);
          boolean contextSwitch = comparison.overall() < config.contextSimilarityThreshold();
          calibration.recordCsd(previous, current, comparison.overall(), comparison.namingStyle(),
              comparison.codingPatterns(), comparison.structuralShape(),
              config.contextSimilarityThreshold(), contextSwitch);
          transitions++;
          similaritySum += comparison.overall();
          minimumSimilarity = Math.min(minimumSimilarity, comparison.overall());
          if (contextSwitch) {
            switches++;
            remediationActions.add(RemediationEffortModel.action(MetricKey.CSD,
                "style:" + blockLocation(current), RemediationEffortModel.Tier.EASY));
            transitionJson.add(contextTransitionJson(previous, current, comparison,
                config.contextSimilarityThreshold(), true));
            findings.add(new DebtFinding(current.file(), current.startLine(), current.endLine(), "context-switch",
                previous.name() + " → " + current.name() + " context similarity=" + format(comparison.overall())
                    + ", threshold=" + format(config.contextSimilarityThreshold())));
          }
        }
      }
    }
    diagnostics.put("csd.switches", (double) switches);
    diagnostics.put("csd.transitions", (double) transitions);
    diagnostics.put("csd.mean_similarity", transitions == 0 ? 0.0 : similaritySum / transitions);
    diagnostics.put("csd.minimum_similarity", transitions == 0 ? 0.0 : minimumSimilarity);
    diagnostics.put("csd.threshold", config.contextSimilarityThreshold());
    artifacts.put("csd.evidence", "{\"model\":\"style-structure-transition-v4\",\"weights\":{"
        + "\"namingStyle\":" + format(config.csdNamingWeight())
        + ",\"codingPatterns\":" + format(config.csdPatternWeight())
        + ",\"structuralShape\":" + format(config.csdStructureWeight()) + "},"
        + "\"thresholdStatus\":\"consensus-adjudicated-v1\",\"switches\":["
        + String.join(",", transitionJson) + "]}");
    return MetricValue.ratio(switches, transitions);
  }

  private ContextComparison compareContext(CodeBlock previous, CodeBlock current) {
    Map<String, Integer> previousNaming = namingStyleVector(previous);
    Map<String, Integer> currentNaming = namingStyleVector(current);
    Map<String, Integer> previousPatterns = previous.stylePatterns();
    Map<String, Integer> currentPatterns = current.stylePatterns();
    double namingStyle = cosine(previousNaming, currentNaming);
    double codingPatterns = cosine(previousPatterns, currentPatterns);
    double structuralShape = structuralSimilarity(previous, current);
    double overall = config.csdNamingWeight() * namingStyle
        + config.csdPatternWeight() * codingPatterns
        + config.csdStructureWeight() * structuralShape;
    return new ContextComparison(overall, namingStyle, codingPatterns, structuralShape);
  }

  private static Map<String, Integer> namingStyleVector(CodeBlock block) {
    Map<String, Integer> result = new HashMap<>();
    addNameShape(result, "function", block.name());
    block.parameters().forEach(name -> addNameShape(result, "parameter", name));
    block.identifiers().forEach(name -> addNameShape(result, "identifier", name));
    return result;
  }

  private static void addNameShape(Map<String, Integer> result, String role, String name) {
    result.put(role + ":case:" + namingConvention(name), 1);
    if ("function".equals(role)) result.put(role + ":tokens:" + sizeBucket(splitName(name).size()), 1);
    if (name.startsWith("_")) result.put(role + ":private-prefix", 1);
    if (name.matches("(?i)^(is|has|can|should|needs|supports)_?.*")) {
      result.put(role + ":boolean-prefix", 1);
    }
  }

  private static String namingConvention(String name) {
    if (name.matches("_*[a-z][a-z0-9]*(?:_[a-z0-9]+)+")) return "snake";
    if (name.matches("_*[a-z][A-Za-z0-9]*[A-Z][A-Za-z0-9]*")) return "camel";
    if (name.matches("[A-Z][A-Za-z0-9]*")) return "pascal";
    if (name.matches("_*[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*")) return "upper";
    if (name.matches("_*[a-z][a-z0-9]*")) return "snake";
    return "mixed";
  }

  private static String sizeBucket(int value) {
    if (value <= 1) return "short";
    if (value <= 3) return "medium";
    return "long";
  }

  private static double proximity(int left, int right) {
    return 1.0 - (double) Math.abs(left - right) / Math.max(1, Math.max(left, right));
  }

  private static double structuralSimilarity(CodeBlock left, CodeBlock right) {
    return (proximity(left.complexity(), right.complexity())
        + proximity(left.maxNesting(), right.maxNesting())
        + proximity(sourceLines(left), sourceLines(right))
        + proximity(left.parameters().size(), right.parameters().size())
        + proximity(left.normalizedTokens().size(), right.normalizedTokens().size())) / 5.0;
  }

  private static int sourceLines(CodeBlock block) {
    return Math.max(1, block.endLine() - block.startLine() + 1);
  }

  private static String contextTransitionJson(CodeBlock previous, CodeBlock current, ContextComparison comparison,
                                              double threshold, boolean contextSwitch) {
    return "{\"file\":\"" + json(previous.file()) + "\",\"scope\":\"" + json(previous.scope())
        + "\",\"from\":" + contextBlockJson(previous)
        + ",\"to\":" + contextBlockJson(current)
        + ",\"similarity\":" + format(comparison.overall()) + ",\"threshold\":" + format(threshold)
        + ",\"switch\":" + contextSwitch + ",\"components\":{\"namingStyle\":" + format(comparison.namingStyle())
        + ",\"codingPatterns\":" + format(comparison.codingPatterns()) + ",\"structuralShape\":"
        + format(comparison.structuralShape()) + "}}";
  }

  private static String contextBlockJson(CodeBlock block) {
    return "{\"name\":\"" + json(block.name()) + "\",\"startLine\":" + block.startLine()
        + ",\"endLine\":" + block.endLine() + "}";
  }

  private record ContextComparison(double overall, double namingStyle, double codingPatterns,
                                   double structuralShape) {}

  private MetricValue rlr(List<CodeBlock> blocks, List<DebtFinding> findings, Map<String, Double> diagnostics,
                          Map<String, String> artifacts,
                          List<RemediationEffortModel.Action> remediationActions) {
    if (blocks.size() < 2) return MetricValue.notApplicable();
    long pairs = 0, redundant = 0;
    boolean budgetReached = false;
    double maximumSyntactic = 0.0, maximumSemantic = 0.0;
    List<String> pairEvidence = new ArrayList<>();
    List<int[]> redundantEdges = new ArrayList<>();
    for (int[] pair : roundRobinPairs(blocks.size())) {
        if (pairs >= config.pairBudget()) { budgetReached = true; break; }
        int i = pair[0], j = pair[1];
        pairs++;
        double syntactic = shingleJaccard(blocks.get(i).normalizedTokens(), blocks.get(j).normalizedTokens());
        boolean hasBehavior = !blocks.get(i).behavior().isEmpty() && !blocks.get(j).behavior().isEmpty();
        RlrBehaviorComparison behaviorComparison = semanticSimilarity(blocks.get(i), blocks.get(j));
        double semantic = behaviorComparison.overall();
        boolean isRedundant = syntactic >= config.syntacticRedundancyThreshold()
            || (hasBehavior && semantic >= config.semanticRedundancyThreshold());
        calibration.recordRlr(blocks.get(i), blocks.get(j), syntactic, behaviorComparison, hasBehavior,
            config.syntacticRedundancyThreshold(), config.semanticRedundancyThreshold(), isRedundant);
        maximumSyntactic = Math.max(maximumSyntactic, syntactic);
        maximumSemantic = Math.max(maximumSemantic, semantic);
        if (isRedundant) {
          redundant++;
          redundantEdges.add(new int[] {i, j});
          CodeBlock first = blocks.get(i), second = blocks.get(j);
          findings.add(new DebtFinding(second.file(), second.startLine(), second.endLine(), "redundant-logic",
              first.name() + " ↔ " + second.name() + ", syntax=" + format(syntactic)
                  + ", semantic=" + format(semantic)));
          if (pairEvidence.size() < MAX_RLR_EVIDENCE_PAIRS) {
            pairEvidence.add(redundantPairJson(first, second, syntactic, semantic));
          }
        }
    }
    diagnostics.put("rlr.redundant", (double) redundant);
    diagnostics.put("rlr.pairs", (double) pairs);
    diagnostics.put("rlr.maximum_syntactic_similarity", maximumSyntactic);
    diagnostics.put("rlr.maximum_semantic_similarity", maximumSemantic);
    diagnostics.put("rlr.syntactic_threshold", config.syntacticRedundancyThreshold());
    diagnostics.put("rlr.semantic_threshold", config.semanticRedundancyThreshold());
    int redundantImplementations = addRedundancyClusterActions(blocks, redundantEdges, remediationActions);
    diagnostics.put("rlr.remediation_actions", (double) redundantImplementations);
    artifacts.put("rlr.evidence", "{\"pairs\":[" + String.join(",", pairEvidence)
        + "],\"reported\":" + pairEvidence.size() + ",\"total\":" + redundant
        + ",\"truncated\":" + (redundant > pairEvidence.size())
        + ",\"analyzedPairs\":" + pairs + ",\"candidatePairs\":" + pairCount(blocks.size())
        + ",\"pairBudget\":" + config.pairBudget()
        + ",\"budgetReached\":" + budgetReached + "}");
    return MetricValue.ratio(redundant, pairs);
  }

  private String redundantPairJson(CodeBlock first, CodeBlock second, double syntactic, double semantic) {
    boolean syntaxMatch = syntactic >= config.syntacticRedundancyThreshold();
    boolean semanticMatch = semantic >= config.semanticRedundancyThreshold();
    String matchedBy = syntaxMatch && semanticMatch ? "syntax-and-semantic-proxy"
        : syntaxMatch ? "syntax" : "semantic-proxy";
    return "{\"first\":" + redundantBlockJson(first) + ",\"second\":" + redundantBlockJson(second)
        + ",\"syntactic\":" + format(syntactic) + ",\"semantic\":" + format(semantic)
        + ",\"matchedBy\":\"" + matchedBy + "\"}";
  }

  private static String redundantBlockJson(CodeBlock block) {
    return "{\"file\":\"" + json(block.file()) + "\",\"name\":\"" + json(block.name())
        + "\",\"startLine\":" + block.startLine() + ",\"endLine\":" + block.endLine() + "}";
  }

  private MetricValue sii(List<ParsedUnit> units, List<DebtFinding> findings, Map<String, Double> diagnostics,
                          Map<String, String> artifacts,
                          List<RemediationEffortModel.Action> remediationActions) {
    List<IdentifierProfile> identifiers = units.stream().flatMap(unit -> unit.identifiers().stream())
        .filter(identifier -> !identifier.semanticFeatures().isEmpty()).toList();
    Map<String, CodeBlock> callableBlocks = new HashMap<>();
    units.stream().flatMap(unit -> unit.blocks().stream())
        .forEach(block -> callableBlocks.put(identifierLocationKey(block.file(), block.startLine()), block));
    if (identifiers.size() < 2) return MetricValue.notApplicable();
    long pairs = 0, inconsistent = 0;
    boolean budgetReached = false;
    long semanticallySimilar = 0;
    double maximumSemantic = 0.0, maximumContext = 0.0, minimumLexicalAmongSimilar = 1.0;
    List<String> pairEvidence = new ArrayList<>();
    List<int[]> inconsistentEdges = new ArrayList<>();
    for (int[] pair : roundRobinPairs(identifiers.size())) {
        if (pairs >= config.pairBudget()) { budgetReached = true; break; }
        int i = pair[0], j = pair[1];
        IdentifierProfile first = identifiers.get(i), second = identifiers.get(j);
        if (!first.kind().equals(second.kind())) continue;
        double context = cosine(first.semanticFeatures(), second.semanticFeatures());
        double semantic = identifierSemanticSimilarity(first.name(), second.name());
        double lexical = normalizedLevenshteinSimilarity(first.name(), second.name());
        boolean excludedByRlr = isRedundantCallablePair(first, second, callableBlocks);
        boolean currentPrediction = !excludedByRlr
            && semantic >= config.semanticConsistencyThreshold()
            && context >= config.semanticContextThreshold()
            && lexical < config.lexicalConsistencyThreshold();
        calibration.recordSii(first, second, semantic, context, lexical, excludedByRlr,
            config.semanticConsistencyThreshold(), config.semanticContextThreshold(),
            config.lexicalConsistencyThreshold(), currentPrediction);
        if (excludedByRlr) continue;
        pairs++;
        maximumSemantic = Math.max(maximumSemantic, semantic);
        maximumContext = Math.max(maximumContext, context);
        boolean comparableConcept = semantic >= config.semanticConsistencyThreshold()
            && context >= config.semanticContextThreshold();
        if (comparableConcept) {
          semanticallySimilar++;
          minimumLexicalAmongSimilar = Math.min(minimumLexicalAmongSimilar, lexical);
        }
        if (comparableConcept && lexical < config.lexicalConsistencyThreshold()) {
          inconsistent++;
          inconsistentEdges.add(new int[] {i, j});
          findings.add(new DebtFinding(second.file(), second.startLine(), second.endLine(),
              "semantic-name-inconsistency", first.kind() + " " + first.name() + " ↔ " + second.name()
                  + ", name-semantic=" + format(semantic) + ", context=" + format(context)
                  + ", lexical=" + format(lexical)));
          if (pairEvidence.size() < MAX_SII_EVIDENCE_PAIRS) {
            pairEvidence.add(inconsistentIdentifierJson(first, second, semantic, context, lexical));
          }
        }
    }
    diagnostics.put("sii.inconsistent", (double) inconsistent);
    diagnostics.put("sii.pairs", (double) pairs);
    diagnostics.put("sii.semantically_similar", (double) semanticallySimilar);
    diagnostics.put("sii.maximum_semantic_similarity", maximumSemantic);
    diagnostics.put("sii.maximum_context_similarity", maximumContext);
    diagnostics.put("sii.minimum_lexical_among_similar", semanticallySimilar == 0 ? 0.0 : minimumLexicalAmongSimilar);
    diagnostics.put("sii.semantic_threshold", config.semanticConsistencyThreshold());
    diagnostics.put("sii.context_threshold", config.semanticContextThreshold());
    diagnostics.put("sii.lexical_threshold", config.lexicalConsistencyThreshold());
    diagnostics.put("sii.identifiers", (double) identifiers.size());
    int renameActions = addNamingClusterActions(identifiers, inconsistentEdges, remediationActions);
    diagnostics.put("sii.remediation_actions", (double) renameActions);
    artifacts.put("sii.evidence", "{\"pairs\":[" + String.join(",", pairEvidence)
        + "],\"reported\":" + pairEvidence.size() + ",\"total\":" + inconsistent
        + ",\"truncated\":" + (inconsistent > pairEvidence.size())
        + ",\"analyzedPairs\":" + pairs + ",\"pairBudget\":" + config.pairBudget()
        + ",\"budgetReached\":" + budgetReached + "}");
    return MetricValue.ratio(inconsistent, pairs);
  }

  private boolean isRedundantCallablePair(IdentifierProfile first, IdentifierProfile second,
                                          Map<String, CodeBlock> callableBlocks) {
    if (!"function".equals(first.kind())) return false;
    CodeBlock left = callableBlocks.get(identifierLocationKey(first.file(), first.startLine()));
    CodeBlock right = callableBlocks.get(identifierLocationKey(second.file(), second.startLine()));
    if (left == null || right == null) return false;
    double syntactic = shingleJaccard(left.normalizedTokens(), right.normalizedTokens());
    boolean hasBehavior = !left.behavior().isEmpty() && !right.behavior().isEmpty();
    return syntactic >= config.syntacticRedundancyThreshold()
        || (hasBehavior && semanticSimilarity(left, right).overall() >= config.semanticRedundancyThreshold());
  }

  private static String identifierLocationKey(String file, int startLine) {
    return file + '\u0000' + startLine;
  }

  private static long pairCount(long items) {
    return items < 2 ? 0 : items * (items - 1) / 2;
  }

  /**
   * Enumerates every unordered pair exactly once using a round-robin tournament schedule.
   * Each complete round touches every item, so a capped traversal is project-wide rather
   * than biased toward the first files in source order.
   */
  private static Iterable<int[]> roundRobinPairs(int size) {
    return () -> new Iterator<>() {
      private final int participants = size % 2 == 0 ? size : size + 1;
      private final int rounds = Math.max(0, participants - 1);
      private final int pairsPerRound = participants / 2;
      private int round;
      private int slot;
      private int[] next = advance();

      @Override
      public boolean hasNext() {
        return next != null;
      }

      @Override
      public int[] next() {
        if (next == null) throw new NoSuchElementException();
        int[] current = next;
        next = advance();
        return current;
      }

      private int[] advance() {
        while (round < rounds) {
          if (slot >= pairsPerRound) {
            round++;
            slot = 0;
            continue;
          }
          int currentSlot = slot++;
          int left;
          int right;
          if (currentSlot == 0) {
            left = participants - 1;
            right = round;
          } else {
            left = (round + currentSlot) % (participants - 1);
            right = (round - currentSlot + participants - 1) % (participants - 1);
          }
          if (left < size && right < size) {
            return left < right ? new int[] {left, right} : new int[] {right, left};
          }
        }
        return null;
      }
    };
  }

  private static String inconsistentIdentifierJson(IdentifierProfile first, IdentifierProfile second,
                                                   double semantic, double context, double lexical) {
    return "{\"first\":" + identifierJson(first) + ",\"second\":" + identifierJson(second)
        + ",\"semantic\":" + format(semantic) + ",\"context\":" + format(context)
        + ",\"lexical\":" + format(lexical) + "}";
  }

  private static double identifierSemanticSimilarity(String left, String right) {
    return cosine(identifierConceptVector(left), identifierConceptVector(right));
  }

  private static Map<String, Integer> identifierConceptVector(String identifier) {
    Map<String, Integer> vector = new HashMap<>();
    for (String token : splitName(identifier)) {
      String concept = IDENTIFIER_CONCEPTS.getOrDefault(token, token);
      vector.merge(concept, 1, Integer::sum);
    }
    return vector;
  }

  private static Map<String, String> identifierConcepts() {
    Map<String, String> result = new HashMap<>();
    concept(result, "access", "fetch", "retrieve", "load", "read");
    concept(result, "compute", "calculate", "compute", "derive");
    concept(result, "aggregate", "sum", "total", "aggregate");
    concept(result, "create", "create", "build", "construct", "make");
    concept(result, "remove", "remove", "delete", "discard");
    concept(result, "update", "update", "modify", "change");
    concept(result, "validate", "validate", "verify", "check");
    concept(result, "search", "find", "search", "locate");
    concept(result, "persist", "save", "store", "write");
    concept(result, "publish", "send", "emit", "publish");
    concept(result, "transform", "convert", "transform");
    concept(result, "initialize", "init", "initialize", "start", "begin");
    concept(result, "finish", "finish", "stop", "end");
    concept(result, "configuration", "config", "configuration", "settings", "options");
    concept(result, "failure", "error", "exception", "failure");
    return Map.copyOf(result);
  }

  private static void concept(Map<String, String> target, String concept, String... words) {
    for (String word : words) target.put(word, concept);
  }

  private static String identifierJson(IdentifierProfile identifier) {
    return "{\"file\":\"" + json(identifier.file()) + "\",\"scope\":\"" + json(identifier.scope())
        + "\",\"name\":\"" + json(identifier.name()) + "\",\"kind\":\"" + json(identifier.kind())
        + "\",\"startLine\":" + identifier.startLine() + ",\"endLine\":" + identifier.endLine() + "}";
  }

  private static double normalizedLevenshteinSimilarity(String left, String right) {
    String a = canonicalIdentifier(left), b = canonicalIdentifier(right);
    int maximum = Math.max(a.length(), b.length());
    if (maximum == 0) return 1.0;
    int[] previous = new int[b.length() + 1];
    int[] current = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) previous[j] = j;
    for (int i = 1; i <= a.length(); i++) {
      current[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
        current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1), substitution);
      }
      int[] swap = previous; previous = current; current = swap;
    }
    return 1.0 - (double) previous[b.length()] / maximum;
  }

  private static String canonicalIdentifier(String value) {
    return value.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').replace('-', ' ')
        .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
  }

  private MetricValue egr(List<CodeBlock> blocks, List<DebtFinding> findings, Map<String, Double> diagnostics,
                          Map<String, String> artifacts,
                          List<RemediationEffortModel.Action> remediationActions) {
    long complex = 0, unexplained = 0;
    long ccTriggered = 0, nestingTriggered = 0, controlFlowTriggered = 0;
    List<String> blockEvidence = new ArrayList<>();
    for (CodeBlock block : blocks) {
      EgrAssessment assessment = egrAssessment(block);
      String rationaleSource = rationaleSource(block);
      boolean hasRationale = !rationaleSource.equals("none");
      calibration.recordEgr(block, assessment.complex(), assessment.ccTriggered(),
          assessment.nestingTriggered(), assessment.controlFlowTriggered(), assessment.flowKinds(),
          hasRationale, rationaleSource, config.complexBlockThreshold(),
          config.complexNestingThreshold(), config.mixedFlowComplexityThreshold(),
          config.mixedFlowKindThreshold());
      if (!assessment.complex()) continue;
      complex++;
      if (assessment.ccTriggered()) ccTriggered++;
      if (assessment.nestingTriggered()) nestingTriggered++;
      if (assessment.controlFlowTriggered()) controlFlowTriggered++;
      if (!hasRationale) {
        unexplained++;
        remediationActions.add(RemediationEffortModel.action(MetricKey.EGR,
            documentationActionKey(block), complexityEffortTier(block)));
        findings.add(new DebtFinding(block.file(), block.startLine(), block.endLine(), "explanation-gap",
            block.name() + " complexity=" + block.complexity() + ", max nesting=" + block.maxNesting()
                + ", triggers=" + String.join(",", assessment.triggers())));
      }
      if (blockEvidence.size() < MAX_EGR_EVIDENCE_BLOCKS) {
        blockEvidence.add(egrBlockJson(block, assessment, hasRationale, rationaleSource));
      }
    }
    diagnostics.put("egr.unexplained", (double) unexplained);
    diagnostics.put("egr.complex", (double) complex);
    diagnostics.put("egr.cc_triggered", (double) ccTriggered);
    diagnostics.put("egr.nesting_triggered", (double) nestingTriggered);
    diagnostics.put("egr.control_flow_triggered", (double) controlFlowTriggered);
    diagnostics.put("egr.cc_threshold", (double) config.complexBlockThreshold());
    diagnostics.put("egr.nesting_threshold", (double) config.complexNestingThreshold());
    artifacts.put("egr.evidence", "{\"blocks\":[" + String.join(",", blockEvidence)
        + "],\"reported\":" + blockEvidence.size() + ",\"total\":" + complex
        + ",\"gaps\":" + unexplained + ",\"truncated\":" + (complex > blockEvidence.size()) + "}");
    return MetricValue.ratio(unexplained, complex);
  }

  private EgrAssessment egrAssessment(CodeBlock block) {
    boolean ccTriggered = block.complexity() >= config.complexBlockThreshold();
    boolean nestingTriggered = block.maxNesting() >= config.complexNestingThreshold();
    List<String> flowKinds = block.behavior().keySet().stream()
        .filter(Set.of("branch", "iteration", "error", "async", "resource")::contains).sorted().toList();
    boolean controlFlowTriggered = block.complexity() >= config.mixedFlowComplexityThreshold()
        && flowKinds.size() >= config.mixedFlowKindThreshold();
    List<String> triggers = new ArrayList<>();
    if (ccTriggered) triggers.add("cyclomatic-complexity");
    if (nestingTriggered) triggers.add("deep-nesting");
    if (controlFlowTriggered) triggers.add("mixed-control-flow");
    return new EgrAssessment(!triggers.isEmpty(), ccTriggered, nestingTriggered,
        controlFlowTriggered, flowKinds, List.copyOf(triggers));
  }

  private static String rationaleSource(CodeBlock block) {
    if (containsRationale(block.docstring())) return "docstring";
    if (containsRationale(block.nearbyComments())) return "comment";
    return "none";
  }

  private static String aisdRemediationKey(DebtFinding finding) {
    String rule = finding.rule().toLowerCase(Locale.ROOT);
    if (rule.equals("specdetect-r5") || rule.equals("opaque-ml-config")) {
      return mlConfigurationActionKey(finding.file(), finding.line());
    }
    return "aisd:" + finding.file() + ':' + finding.line() + ':' + rule;
  }

  private static RemediationEffortModel.Tier aisdEffortTier(String ruleName) {
    String rule = ruleName.toLowerCase(Locale.ROOT).replace("specdetect-", "");
    if (Set.of("r4", "r7", "r9", "r10", "r11", "r11bis", "r22").contains(rule)) {
      return RemediationEffortModel.Tier.MAJOR;
    }
    if (Set.of("r2", "r6", "r8", "r12", "r13", "r14", "r15", "r16", "r18", "r20", "r21", "r24")
        .contains(rule)) {
      return RemediationEffortModel.Tier.EASY;
    }
    return RemediationEffortModel.Tier.MODERATE;
  }

  private static String mlConfigurationActionKey(String file, int line) {
    return "ml-configuration:" + file + ':' + line;
  }

  private static String documentationActionKey(CodeBlock block) {
    return "documentation:" + blockLocation(block);
  }

  private static String blockLocation(CodeBlock block) {
    return block.file() + ':' + block.startLine() + ':' + block.endLine();
  }

  private static RemediationEffortModel.Tier complexityEffortTier(CodeBlock block) {
    int decisions = Math.max(0, block.complexity() - 1);
    if (decisions > 5) return RemediationEffortModel.Tier.MAJOR;
    if (decisions > 2) return RemediationEffortModel.Tier.MODERATE;
    return RemediationEffortModel.Tier.EASY;
  }

  private static int addRedundancyClusterActions(List<CodeBlock> blocks, List<int[]> edges,
                                                 List<RemediationEffortModel.Action> actions) {
    List<List<Integer>> clusters = connectedComponents(blocks.size(), edges);
    int count = 0;
    for (List<Integer> cluster : clusters) {
      if (cluster.size() < 2) continue;
      List<String> locations = cluster.stream().map(index -> blockLocation(blocks.get(index))).sorted().toList();
      String canonical = locations.get(0);
      for (int i = 1; i < locations.size(); i++) {
        actions.add(RemediationEffortModel.action(MetricKey.RLR,
            "redundancy:" + canonical + ':' + locations.get(i), RemediationEffortModel.Tier.MAJOR));
        count++;
      }
    }
    return count;
  }

  private static int addNamingClusterActions(List<IdentifierProfile> identifiers, List<int[]> edges,
                                             List<RemediationEffortModel.Action> actions) {
    List<List<Integer>> clusters = connectedComponents(identifiers.size(), edges);
    int count = 0;
    for (List<Integer> cluster : clusters) {
      List<String> names = cluster.stream().map(index -> canonicalIdentifier(identifiers.get(index).name()))
          .distinct().sorted().toList();
      if (names.size() < 2) continue;
      String clusterKey = identifiers.get(cluster.get(0)).kind() + ':' + names.get(0);
      for (int i = 1; i < names.size(); i++) {
        actions.add(RemediationEffortModel.action(MetricKey.SII,
            "naming:" + clusterKey + ':' + names.get(i), RemediationEffortModel.Tier.MODERATE));
        count++;
      }
    }
    return count;
  }

  private static List<List<Integer>> connectedComponents(int size, List<int[]> edges) {
    int[] parent = new int[size];
    for (int i = 0; i < size; i++) parent[i] = i;
    for (int[] edge : edges) union(parent, edge[0], edge[1]);
    Map<Integer, List<Integer>> groups = new HashMap<>();
    for (int[] edge : edges) {
      for (int vertex : edge) groups.computeIfAbsent(find(parent, vertex), ignored -> new ArrayList<>()).add(vertex);
    }
    List<List<Integer>> result = new ArrayList<>();
    for (List<Integer> group : groups.values()) {
      List<Integer> unique = group.stream().distinct().sorted().toList();
      result.add(unique);
    }
    return result;
  }

  private static int find(int[] parent, int value) {
    if (parent[value] != value) parent[value] = find(parent, parent[value]);
    return parent[value];
  }

  private static void union(int[] parent, int left, int right) {
    int leftRoot = find(parent, left), rightRoot = find(parent, right);
    if (leftRoot != rightRoot) parent[rightRoot] = leftRoot;
  }

  private static boolean containsRationale(String text) {
    if (text == null || text.isBlank()) return false;
    String normalized = text.toLowerCase(Locale.ROOT);
    for (String term : RATIONALE_TERMS) {
      String expression = "(?<![a-z0-9_])" + Pattern.quote(term).replace("\\ ", "\\s+")
          + "(?![a-z0-9_])";
      if (Pattern.compile(expression).matcher(normalized).find()) return true;
    }
    return false;
  }

  private static String egrBlockJson(CodeBlock block, EgrAssessment assessment,
                                     boolean hasRationale, String rationaleSource) {
    return "{\"file\":\"" + json(block.file()) + "\",\"name\":\"" + json(block.name())
        + "\",\"startLine\":" + block.startLine() + ",\"endLine\":" + block.endLine()
        + ",\"complexity\":" + block.complexity() + ",\"nesting\":" + block.maxNesting()
        + ",\"controlFlow\":" + jsonArray(assessment.flowKinds())
        + ",\"triggers\":" + jsonArray(assessment.triggers())
        + ",\"hasRationale\":" + hasRationale + ",\"rationaleSource\":\""
        + rationaleSource + "\"}";
  }

  private record EgrAssessment(boolean complex, boolean ccTriggered, boolean nestingTriggered,
                               boolean controlFlowTriggered, List<String> flowKinds,
                               List<String> triggers) {}

  private static String resolveImport(String source, String imported, List<ParsedUnit> units) {
    long leadingDots = imported.chars().takeWhile(character -> character == '.').count();
    String module = imported.substring((int) leadingDots);
    String resolvedModule = module;
    if (leadingDots > 0) {
      String sourceModule = moduleName(source);
      List<String> packageParts = new ArrayList<>(List.of(sourceModule.split("\\.")));
      if (!source.replace('\\', '/').endsWith("/__init__.py") && !packageParts.isEmpty()) {
        packageParts.remove(packageParts.size() - 1);
      }
      int ascents = Math.max(0, (int) leadingDots - 1);
      while (ascents-- > 0 && !packageParts.isEmpty()) packageParts.remove(packageParts.size() - 1);
      if (!module.isBlank()) packageParts.addAll(List.of(module.split("\\.")));
      resolvedModule = String.join(".", packageParts);
    }
    for (ParsedUnit unit : units) {
      String candidate = moduleName(unit.source().path());
      if (candidate.equals(resolvedModule) || candidate.endsWith('.' + resolvedModule)) return unit.source().path();
    }
    return null;
  }

  private static String moduleName(String path) {
    String normalized = path.replace('\\', '/').replaceFirst("\\.py$", "");
    if (normalized.endsWith("/__init__")) normalized = normalized.substring(0, normalized.length() - 9);
    return normalized.replace('/', '.').replaceAll("^\\.+", "");
  }

  private static double cosine(Map<String, Integer> a, Map<String, Integer> b) {
    if (a.isEmpty() && b.isEmpty()) return 1.0;
    double dot = 0, aa = 0, bb = 0;
    Set<String> keys = new HashSet<>(a.keySet()); keys.addAll(b.keySet());
    for (String key : keys) {
      double x = a.getOrDefault(key, 0), y = b.getOrDefault(key, 0);
      dot += x * y; aa += x * x; bb += y * y;
    }
    return aa == 0 || bb == 0 ? 0.0 : dot / Math.sqrt(aa * bb);
  }

  private static Map<String, Integer> contextVector(CodeBlock block) {
    Map<String, Integer> result = new HashMap<>(block.behavior());
    result.put("complexity", block.complexity());
    result.put("nesting", block.maxNesting());
    for (String call : block.callSequence()) result.merge("call:" + callFamily(call), 1, Integer::sum);
    return result;
  }

  private RlrBehaviorComparison semanticSimilarity(CodeBlock left, CodeBlock right) {
    double behavior = cosine(contextVector(left), contextVector(right));
    double calls = setJaccard(new HashSet<>(left.callSequence()), new HashSet<>(right.callSequence()));
    double outputs = setJaccard(left.returnTokens(), right.returnTokens());
    double overall = config.rlrBehaviorWeight() * behavior
        + config.rlrCallsWeight() * calls
        + config.rlrOutputsWeight() * outputs;
    return new RlrBehaviorComparison(overall, behavior, calls, outputs);
  }

  record RlrBehaviorComparison(double overall, double behaviorContext, double calls, double outputs) {}

  private static String callFamily(String call) {
    String lower = call.toLowerCase(Locale.ROOT);
    if (lower.contains("read") || lower.contains("load") || lower.contains("open")) return "input";
    if (lower.contains("write") || lower.contains("save") || lower.contains("dump")
        || lower.equals("print") || lower.contains("log")) return "output";
    if (lower.contains("fit") || lower.contains("train")) return "train";
    if (lower.contains("predict") || lower.contains("transform")) return "infer";
    if (lower.contains("request") || lower.contains("http") || lower.contains("socket")
        || lower.contains("aiohttp") || lower.contains("urllib")) return "network";
    if (lower.contains("query") || lower.contains("execute") || lower.contains("cursor")
        || lower.contains("database") || lower.contains("sql")) return "database";
    if (lower.contains("plot") || lower.contains("chart") || lower.contains("visual")) return "visualization";
    return "local-or-other";
  }

  private static double setJaccard(Set<String> left, Set<String> right) {
    if (left.isEmpty() && right.isEmpty()) return 1.0;
    Set<String> union = new HashSet<>(left); union.addAll(right);
    Set<String> intersection = new HashSet<>(left); intersection.retainAll(right);
    return union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
  }

  private static double shingleJaccard(List<String> a, List<String> b) {
    Set<String> left = shingles(a, 4), right = shingles(b, 4);
    if (left.isEmpty() && right.isEmpty()) return 1.0;
    Set<String> union = new HashSet<>(left); union.addAll(right);
    Set<String> intersection = new HashSet<>(left); intersection.retainAll(right);
    return (double) intersection.size() / union.size();
  }

  private static Set<String> shingles(List<String> tokens, int width) {
    Set<String> result = new HashSet<>();
    if (tokens.size() < width) { result.add(String.join(" ", tokens)); return result; }
    for (int i = 0; i + width <= tokens.size(); i++) result.add(String.join(" ", tokens.subList(i, i + width)));
    return result;
  }

  private static Set<String> splitName(String name) {
    String split = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').replace('-', ' ');
    Set<String> result = new HashSet<>();
    for (String token : split.toLowerCase(Locale.ROOT).split("\\s+")) if (!token.isBlank()) result.add(token);
    return result;
  }

  private static String format(double value) { return String.format(Locale.ROOT, "%.3f", value); }

  private static String dependencyJson(String source, int line, String module, String kind, String target) {
    return "{\"source\":\"" + json(source) + "\",\"line\":" + line + ",\"module\":\""
        + json(module) + "\",\"kind\":\"" + kind + "\",\"target\":"
        + (target == null ? "null" : "\"" + json(target) + "\"") + "}";
  }

  private static String edgeJson(DependencyEdge edge) {
    return "{\"source\":\"" + json(edge.source()) + "\",\"line\":" + edge.line()
        + ",\"module\":\"" + json(edge.module()) + "\",\"target\":\"" + json(edge.target()) + "\"}";
  }

  private static List<Set<String>> stronglyConnectedComponents(List<ParsedUnit> units,
                                                                List<DependencyEdge> edges) {
    Map<String, List<String>> graph = new HashMap<>(), reverse = new HashMap<>();
    for (ParsedUnit unit : units) {
      graph.put(unit.source().path(), new ArrayList<>());
      reverse.put(unit.source().path(), new ArrayList<>());
    }
    for (DependencyEdge edge : edges) {
      graph.get(edge.source()).add(edge.target());
      reverse.get(edge.target()).add(edge.source());
    }
    Set<String> visited = new HashSet<>();
    List<String> order = new ArrayList<>();
    for (String node : graph.keySet()) finishOrder(node, graph, visited, order);
    Collections.reverse(order);
    visited.clear();
    List<Set<String>> result = new ArrayList<>();
    for (String node : order) {
      if (visited.contains(node)) continue;
      Set<String> component = new LinkedHashSet<>();
      collectComponent(node, reverse, visited, component);
      result.add(component);
    }
    return result;
  }

  private static void finishOrder(String node, Map<String, List<String>> graph,
                                  Set<String> visited, List<String> order) {
    if (!visited.add(node)) return;
    for (String next : graph.getOrDefault(node, List.of())) finishOrder(next, graph, visited, order);
    order.add(node);
  }

  private static void collectComponent(String node, Map<String, List<String>> graph,
                                       Set<String> visited, Set<String> component) {
    if (!visited.add(node)) return;
    component.add(node);
    for (String next : graph.getOrDefault(node, List.of())) collectComponent(next, graph, visited, component);
  }

  private record DependencyEdge(String source, int line, String module, String target) {}

  private static String json(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
  }

  private static String jsonArray(java.util.Collection<String> values) {
    return values.stream().sorted().map(value -> "\"" + json(value) + "\"")
        .collect(java.util.stream.Collectors.joining(",", "[", "]"));
  }
}
