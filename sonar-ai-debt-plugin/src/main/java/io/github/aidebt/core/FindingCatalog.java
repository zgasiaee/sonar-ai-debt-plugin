package io.github.aidebt.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stable identifiers and remediation metadata for source-located Python debt findings. */
public final class FindingCatalog {
  private static final Map<String, RuleSpec> RULES = buildRules();
  private static final Pattern CALL =
      Pattern.compile("([A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*)*)\\s*\\(");
  private static final Pattern QUOTED_SUBSCRIPT =
      Pattern.compile("\\[\\s*(['\"])([^'\"]+)\\1\\s*\\]");

  private FindingCatalog() {}

  public static Map<String, RuleSpec> rules() { return RULES; }

  public static RuleSpec rule(String key) {
    RuleSpec rule = RULES.get(key);
    if (rule == null) throw new IllegalArgumentException("Unknown AI Debt rule: " + key);
    return rule;
  }

  /**
   * Returns remediation tailored to the reported rule and source expression.
   *
   * <p>The catalog text remains the safe fallback when the external report points to a missing
   * source line. No value or parameter choice is invented: where the correct value is
   * experiment-dependent, the recommendation names the decision that must be made and recorded.
   */
  public static String recommendation(String key, String evidence, String sourceLine) {
    RuleSpec specification = rule(key);
    String call = callName(sourceLine);
    String receiver;
    return switch (key) {
      case "specdetect-r1" ->
          "At " + call + ", remove materialized tiling only if the operand dimensions are "
              + "broadcast-compatible; use the direct broadcasted operation, or "
              + "tf.broadcast_to(...) only when an explicit target shape is required.";
      case "specdetect-r2" ->
          "Set the reproducibility parameter on " + call
              + " (for example random_state=<recorded_seed>) and initialize every upstream "
              + "Python, NumPy, or framework RNG used by this execution path with that recorded seed.";
      case "specdetect-r3" ->
          "Replace repeated tensor growth at " + call
              + " with tf.TensorArray(dtype=..., size=<loop_bound>), write each loop result by "
              + "index, and stack once after the loop.";
      case "specdetect-r4" ->
          "Use eval() only around validation for this model, run inference under "
              + "torch.no_grad(), and restore train() on the same model before the next training step.";
      case "specdetect-r5" ->
          "Add explicit behavior-defining keyword arguments to " + call
              + " and persist their resolved values with the run; include the estimator's random "
              + "seed and the parameters that control capacity, optimization, or stopping.";
      case "specdetect-r6" ->
          "Call torch.use_deterministic_algorithms(True) before the flagged computation; also seed "
              + "the participating RNGs and disable backend benchmarking when reproducibility is required.";
      case "specdetect-r7" ->
          "Before this logarithm, validate the input domain and guard non-positive values with "
              + "tf.clip_by_value(x, epsilon, ...) or an explicit tf.where mask chosen for the model.";
      case "specdetect-r8" -> {
        receiver = receiverBefore(sourceLine, "forward");
        yield "Replace the direct .forward(...) invocation with "
            + (receiver.isBlank() ? "model(...)" : receiver + "(...)")
            + " so PyTorch hooks, wrappers, and Module.__call__ behavior are preserved.";
      }
      case "specdetect-r9" ->
          "Unless gradient accumulation is intentional, place optimizer.zero_grad(set_to_none=True) "
              + "immediately before the flagged backward() call, followed by backward() and optimizer.step().";
      case "specdetect-r10" ->
          "Release the flagged object's retained graph at its lifecycle boundary: detach values kept "
              + "for logging, delete references no longer needed, and use framework cache/session cleanup "
              + "only after the owning work is complete.";
      case "specdetect-r11" ->
          "Split the raw dataset before fitting any transformer; place preprocessing and the estimator "
              + "in sklearn.pipeline.Pipeline so fit() learns state from the training fold only.";
      case "specdetect-r11bis" ->
          "Wrap the preprocessing steps and estimator used by this model.fit path in Pipeline or "
              + "make_pipeline, and pass the pipeline—not separately transformed data—to validation.";
      case "specdetect-r12" ->
          "If both operands represent matrices, replace this operation with A @ B or np.matmul(A, B) "
              + "after asserting the intended shapes; retain dot only when its 1-D semantics are intentional.";
      case "specdetect-r13" -> {
        String column = quotedColumn(sourceLine);
        yield "Initialize " + (column.isBlank() ? "the flagged column" : "column '" + column + "'")
            + " with pd.NA/np.nan, or a typed pd.Series when its dtype is known; keep zero or an "
            + "empty string only when it is a valid domain value rather than a missing-value placeholder.";
      }
      case "specdetect-r14" -> {
        receiver = receiverBefore(sourceLine, "values");
        yield "Replace " + (receiver.isBlank() ? "the flagged .values access" : receiver + ".values")
            + " with " + (receiver.isBlank() ? "frame.to_numpy(...)" : receiver + ".to_numpy(...)")
            + "; pass dtype= and copy= only when the consumer requires those guarantees.";
      }
      case "specdetect-r15" ->
          "On this merge/join, specify on=<validated_key> or left_on=/right_on= explicitly and set "
              + "validate= to the expected one-to-one, one-to-many, or many-to-one relationship.";
      case "specdetect-r16" ->
          "Assign the return value of " + call
              + " back to the intended variable; use inplace=True only if this exact API documents "
              + "support for it and mutation is intentional.";
      case "specdetect-r17" ->
          "Replace this row/element loop with the equivalent vectorized Pandas, NumPy, or tensor "
              + "expression, then verify identical null handling, shape, and dtype on a representative sample.";
      case "specdetect-r18" ->
          "Replace the flagged equality comparison with pd.isna(value) for Pandas data or "
              + "np.isnan(value) for numeric NumPy values; use the matching negated predicate for != NaN.";
      case "specdetect-r19" ->
          "Pre-register the primary validation metric and threshold-selection rule, tune inside nested "
              + "cross-validation when applicable, and report sensitivity instead of selecting the best "
              + "result from many thresholds after evaluation.";
      case "specdetect-r20" ->
          "Collapse this chained DataFrame access into one .loc[row_selector, column_selector] operation; "
              + "for assignment, write through that single .loc expression.";
      case "specdetect-r21" ->
          "At " + call
              + ", provide dtype={column: dtype, ...} for schema-critical columns and usecols=[...] "
              + "when the accepted schema is fixed; validate required columns immediately after ingestion.";
      case "specdetect-r22" ->
          "Place StandardScaler or the domain-appropriate scaler before the scale-sensitive estimator "
              + "inside a Pipeline, and fit the complete pipeline on training folds only.";
      case "specdetect-r23" ->
          "Add keras.callbacks.EarlyStopping(monitor=<validation_metric>, mode=<min_or_max>, "
              + "patience=<justified_epochs>, restore_best_weights=True) to this fit(..., callbacks=[...]) call.";
      case "specdetect-r24" ->
          "At " + call
              + ", set index_col=<semantic_key> when a source column is the row identity, or call "
              + "set_index(<semantic_key>) immediately after reading; keep the RangeIndex only when it is intentional.";
      default -> specification.remediation();
    };
  }

  private static String callName(String sourceLine) {
    if (sourceLine == null || sourceLine.isBlank()) return "the flagged call";
    Matcher matcher = CALL.matcher(sourceLine);
    String result = "";
    while (matcher.find()) result = matcher.group(1);
    return result.isBlank() ? "the flagged call" : result + "(...)";
  }

  private static String receiverBefore(String sourceLine, String member) {
    if (sourceLine == null || sourceLine.isBlank()) return "";
    Matcher matcher = Pattern.compile(
        "([A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*)*)\\." + Pattern.quote(member) + "\\b")
        .matcher(sourceLine);
    return matcher.find() ? matcher.group(1) : "";
  }

  private static String quotedColumn(String sourceLine) {
    if (sourceLine == null || sourceLine.isBlank()) return "";
    Matcher matcher = QUOTED_SUBSCRIPT.matcher(sourceLine);
    return matcher.find() ? matcher.group(2) : "";
  }

  private static Map<String, RuleSpec> buildRules() {
    Map<String, RuleSpec> rules = new LinkedHashMap<>();
    add(rules, "AIDEBT-PY-001", "mutable-default", "Mutable default argument", "MEDIUM",
        "Mutable defaults are shared between calls and can create hidden state.",
        "Use None as the default and initialize the mutable value inside the function.");
    add(rules, "AIDEBT-PY-002", "broad-except", "Broad exception handler", "MEDIUM",
        "Broad handlers hide the actual failure contract and make recovery behavior ambiguous.",
        "Catch the narrowest expected exception and preserve unexpected failures.");
    add(rules, "AIDEBT-PY-003", "swallowed-exception", "Swallowed exception", "HIGH",
        "Silently discarding an exception removes failure evidence and can leave partial state.",
        "Handle, translate, or log the exception; otherwise document and re-raise it.");
    add(rules, "AIDEBT-PY-004", "wildcard-import", "Wildcard import", "LOW",
        "Wildcard imports obscure dependencies and can change behavior when an upstream module changes.",
        "Import the required symbols explicitly.");
    add(rules, "AIDEBT-PY-005", "debug-output", "Debug output in production code", "LOW",
        "Unstructured output is difficult to control, search, and correlate in production.",
        "Remove the print call or replace it with intentional structured logging.");
    add(rules, "AIDEBT-PY-006", "unsafe-eval", "Unsafe dynamic evaluation", "HIGH",
        "Dynamic evaluation expands the attack surface and makes behavior difficult to reason about.",
        "Use a constrained parser, literal_eval, or explicit operation dispatch.");
    add(rules, "AIDEBT-PY-007", "placeholder", "Incomplete placeholder", "MEDIUM",
        "Placeholder code can silently ship an incomplete behavior path.",
        "Implement the behavior or fail explicitly at a clearly isolated boundary.");
    add(rules, "AIDEBT-PY-008", "hardcoded-secret", "Potential hard-coded secret", "HIGH",
        "Credentials embedded in source can leak through version control and build artifacts.",
        "Move the value to a managed secret source and rotate any exposed credential.");
    add(rules, "AIDEBT-PY-009", "training-on-evaluation-data", "Evaluation leakage candidate", "HIGH",
        "Fitting state on test or validation data can invalidate the reported model performance.",
        "Fit preprocessing and model state on training data only, then transform evaluation data.");
    add(rules, "AIDEBT-PY-010", "opaque-ml-config", "Opaque ML configuration", "MEDIUM",
        "Configuration keys that cannot be traced make experiments harder to reproduce and audit.",
        "Use a typed or literal configuration whose effective keys are recorded with the run.");
    add(rules, "AIDEBT-PY-011", "missing-random-seed", "Missing random seed", "MEDIUM",
        "Unseeded stochastic components can produce irreproducible results.",
        "Set random_state or the framework-specific seed and record it with experiment metadata.");
    add(rules, "AIDEBT-PY-012", "unpinned-model-revision", "Unpinned model revision", "MEDIUM",
        "A moving pretrained-model reference can resolve to different artifacts over time.",
        "Pin an immutable model revision or commit hash.");
    add(rules, "AIDEBT-PY-013", "context-switch", "Abrupt style–structure context switch", "LOW",
        "A sharp break in naming conventions, coding idioms, and structural shape can force readers to rebuild their mental pattern.",
        "Align neighboring naming and coding idioms, or make an intentional style boundary explicit through grouping and documentation.");
    add(rules, "AIDEBT-PY-014", "redundant-logic", "Redundant logic candidate", "MEDIUM",
        "Similar implementations can drift independently and multiply maintenance effort.",
        "Review both functions and extract a shared abstraction when their contracts are genuinely equivalent.");
    add(rules, "AIDEBT-PY-015", "semantic-name-inconsistency", "Semantic naming inconsistency", "LOW",
        "Similar behavior under dissimilar declared intent makes code harder to discover and predict.",
        "Align names and parameter terminology with the behavior or separate the underlying responsibilities.");
    add(rules, "AIDEBT-PY-016", "explanation-gap", "Complex block without rationale", "MEDIUM",
        "A complex decision path without rationale leaves maintainers to reconstruct intent from control flow.",
        "Document why the non-obvious decisions, invariants, or edge cases are necessary.");
    add(rules, "AIDEBT-PY-017", "multiply-nested-container", "Multiply-nested container", "MEDIUM",
        "Deeply nested containers reduce readability and can obscure indexing or shape defects.",
        "Introduce named records, dataclasses, or intermediate variables instead of anonymous deep nesting.");
    add(rules, "AIDEBT-PY-018", "long-parameter-list", "Long parameter list", "MEDIUM",
        "Many parameters increase call-site complexity and often indicate multiple responsibilities.",
        "Group cohesive values in a typed configuration object or split the responsibility.");
    add(rules, "AIDEBT-PY-019", "long-method", "Long method", "MEDIUM",
        "Long functions require more context to understand, test, and change safely.",
        "Extract cohesive operations into well-named functions while preserving the public contract.");
    add(rules, "AIDEBT-PY-020", "long-lambda", "Long lambda function", "LOW",
        "A long anonymous function hides intent and is difficult to test independently.",
        "Replace it with a named function.");
    add(rules, "AIDEBT-PY-021", "long-ternary", "Long ternary expression", "LOW",
        "A lengthy conditional expression compresses multiple decisions into a hard-to-scan form.",
        "Use an explicit conditional or extract a named decision function.");
    add(rules, "AIDEBT-PY-022", "complex-comprehension", "Complex container comprehension", "MEDIUM",
        "Multiple generators and filters in one comprehension increase hidden control-flow complexity.",
        "Decompose the transformation into named steps or a conventional loop.");
    add(rules, "AIDEBT-PY-023", "long-message-chain", "Long message chain", "MEDIUM",
        "A deep attribute or method chain couples code to an extended object navigation path.",
        "Hide navigation behind a stable method or introduce meaningful intermediate values.");
    add(rules, "AIDEBT-PY-024", "large-class", "Large class", "MEDIUM",
        "A large class commonly accumulates unrelated responsibilities and change reasons.",
        "Separate cohesive responsibilities into smaller collaborating classes.");
    add(rules, "AIDEBT-PY-025", "coupling-cycle", "Internal dependency cycle", "HIGH",
        "A dependency cycle prevents the participating modules from changing or being reused independently.",
        "Break the cycle by moving the shared contract to a lower-level module or by inverting one dependency.");
    add(rules, "AIDEBT-PY-026", "unstable-dependency-direction", "Dependency toward a less stable module", "MEDIUM",
        "A more stable module depending on a less stable module propagates change toward code with more dependents.",
        "Review the import boundary and depend on a stable abstraction, invert the dependency, or merge modules that form one cohesive responsibility.");
    addSpec(rules, "R1", "Broadcasting feature not used", "MEDIUM",
        "Explicit tensor tiling can duplicate memory when broadcasting would suffice.",
        "Review the operation and replace tf.tile with broadcasting when shapes permit it.");
    addSpec(rules, "R2", "Random seed not set", "MEDIUM",
        "Missing framework or library seeds undermine repeatability.",
        "Set and record every relevant random seed before stochastic operations.");
    addSpec(rules, "R3", "TensorArray not used", "MEDIUM",
        "Repeated tensor growth in a loop can cause avoidable allocation and copying.",
        "Use TensorArray or an equivalent buffered/vectorized construction.");
    addSpec(rules, "R4", "Training/evaluation mode improper toggling", "HIGH",
        "Leaving a PyTorch model in evaluation mode can silently change later training behavior.",
        "Restore train mode after validation or make the lifecycle boundary explicit.");
    addSpec(rules, "R5", "Hyperparameter not explicitly set", "MEDIUM",
        "Implicit defaults weaken auditability and can change across library versions.",
        "Specify and record the behavior-defining hyperparameters.");
    addSpec(rules, "R6", "Deterministic algorithm option not used", "MEDIUM",
        "PyTorch may select nondeterministic kernels unless deterministic execution is requested.",
        "Enable torch.use_deterministic_algorithms(True) where reproducibility is required.");
    addSpec(rules, "R7", "Missing invalid-value mask", "HIGH",
        "Taking a logarithm of non-positive values can introduce NaN or infinity.",
        "Mask or clip the input before the logarithm.");
    addSpec(rules, "R8", "PyTorch call method misused", "HIGH",
        "Calling forward directly bypasses Module call hooks and framework wrappers.",
        "Invoke the module instance instead of calling .forward() directly.");
    addSpec(rules, "R9", "Gradients not cleared before backpropagation", "HIGH",
        "Unintended gradient accumulation can corrupt optimization behavior.",
        "Clear gradients in the intended order before backward propagation.");
    addSpec(rules, "R10", "Memory not freed", "MEDIUM",
        "Heavy tensors or models without cleanup can retain computation graphs and exhaust memory.",
        "Detach, delete, or clear framework state at the appropriate lifecycle boundary.");
    addSpec(rules, "R11", "Data leakage before split", "HIGH",
        "Fitting preprocessing state before the train/test split leaks holdout information.",
        "Split first and fit preprocessing inside a training-only pipeline.");
    addSpec(rules, "R11bis", "Data leakage without pipeline", "HIGH",
        "Separated preprocessing and model fitting can apply learned transforms outside validation folds.",
        "Compose preprocessing and estimation in a Pipeline.");
    addSpec(rules, "R12", "Matrix multiplication API misused", "MEDIUM",
        "np.dot has dimension-dependent semantics that can obscure matrix intent.",
        "Use np.matmul or the @ operator for explicit matrix multiplication.");
    addSpec(rules, "R13", "Empty column misinitialization", "MEDIUM",
        "Dummy zero or empty-string initialization can hide missingness and force an unintended dtype.",
        "Use an explicit missing value or a typed empty Series.");
    addSpec(rules, "R14", "DataFrame conversion API misused", "LOW",
        "DataFrame.values can discard dtype intent and offers no conversion control.",
        "Use DataFrame.to_numpy with an explicit dtype when needed.");
    addSpec(rules, "R15", "Merge key not explicitly set", "HIGH",
        "Implicit merge keys can change silently when schemas evolve.",
        "Specify on or left_on/right_on explicitly.");
    addSpec(rules, "R16", "API result discarded", "HIGH",
        "A transformation whose result is neither assigned nor applied in place may be a no-op.",
        "Assign the returned value or request supported in-place behavior explicitly.");
    addSpec(rules, "R17", "Unnecessary iteration", "MEDIUM",
        "Row-wise Python iteration can defeat vectorized CPU/GPU execution.",
        "Use a vectorized Pandas, NumPy, or TensorFlow operation when equivalent.");
    addSpec(rules, "R18", "NaN comparison", "HIGH",
        "Equality comparison with NaN never provides a reliable missing-value test.",
        "Use isna, isnan, or the framework-specific missing-value predicate.");
    addSpec(rules, "R19", "Threshold validation metrics count", "MEDIUM",
        "Selecting outcomes from many threshold-dependent metrics can inflate evaluation optimism.",
        "Predefine a focused metric set and report threshold sensitivity.");
    addSpec(rules, "R20", "Chained DataFrame indexing", "HIGH",
        "Chained indexing can operate on a view or copy and make assignments unreliable.",
        "Use a single .loc or .iloc operation.");
    addSpec(rules, "R21", "Read schema not explicitly set", "MEDIUM",
        "Inferred columns and dtypes make ingestion behavior dependent on input samples.",
        "Specify columns and dtypes in the DataFrame read operation.");
    addSpec(rules, "R22", "No scaling before scale-sensitive operation", "HIGH",
        "Scale-sensitive models can be dominated by features with larger numeric ranges.",
        "Fit scaling on training data and include it in the model pipeline.");
    addSpec(rules, "R23", "Early stopping not used", "MEDIUM",
        "Training without an early-stopping policy may waste computation and overfit.",
        "Configure an EarlyStopping callback with an intentional monitored metric and patience.");
    addSpec(rules, "R24", "Index column not explicitly set", "MEDIUM",
        "Implicit RangeIndex creation can hide the semantic row identity during later alignment.",
        "Specify index_col at ingestion or set the semantic index explicitly.");
    return Map.copyOf(rules);
  }

  private static void addSpec(Map<String, RuleSpec> rules, String ruleId, String title, String severity,
                              String rationale, String remediation) {
    String key = "specdetect-" + ruleId.toLowerCase(java.util.Locale.ROOT);
    add(rules, "SPECDETECT4AI-" + ruleId, key, title, severity, rationale, remediation);
  }

  private static void add(Map<String, RuleSpec> rules, String id, String key, String title, String severity,
                          String rationale, String remediation) {
    rules.put(key, new RuleSpec(id, key, title, severity, rationale, remediation));
  }

  public record RuleSpec(String id, String key, String title, String severity,
                         String rationale, String remediation) {}
}
