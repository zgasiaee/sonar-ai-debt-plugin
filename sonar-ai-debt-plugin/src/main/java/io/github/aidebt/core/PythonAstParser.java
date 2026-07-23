package io.github.aidebt.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sonar.plugins.python.api.Parser;
import org.sonar.plugins.python.api.tree.AliasedName;
import org.sonar.plugins.python.api.tree.Argument;
import org.sonar.plugins.python.api.tree.AssignmentStatement;
import org.sonar.plugins.python.api.tree.BaseTreeVisitor;
import org.sonar.plugins.python.api.tree.BinaryExpression;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.ComprehensionFor;
import org.sonar.plugins.python.api.tree.ComprehensionIf;
import org.sonar.plugins.python.api.tree.DictionaryLiteral;
import org.sonar.plugins.python.api.tree.ExceptClause;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.ForStatement;
import org.sonar.plugins.python.api.tree.FunctionDef;
import org.sonar.plugins.python.api.tree.IfStatement;
import org.sonar.plugins.python.api.tree.ImportFrom;
import org.sonar.plugins.python.api.tree.ImportName;
import org.sonar.plugins.python.api.tree.ListLiteral;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.Parameter;
import org.sonar.plugins.python.api.tree.PassStatement;
import org.sonar.plugins.python.api.tree.QualifiedExpression;
import org.sonar.plugins.python.api.tree.RegularArgument;
import org.sonar.plugins.python.api.tree.ReturnStatement;
import org.sonar.plugins.python.api.tree.SetLiteral;
import org.sonar.plugins.python.api.tree.Token;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.TryStatement;
import org.sonar.plugins.python.api.tree.WhileStatement;
import org.sonar.plugins.python.api.tree.WithStatement;
import org.sonar.plugins.python.api.tree.UnpackingExpression;
import org.sonar.plugins.python.api.tree.KeyValuePair;
import org.sonar.plugins.python.api.tree.StringLiteral;
import org.sonar.plugins.python.api.tree.EllipsisExpression;
import org.sonar.plugins.python.api.tree.RaiseStatement;
import org.sonar.plugins.python.api.tree.ClassDef;
import org.sonar.plugins.python.api.tree.LambdaExpression;
import org.sonar.plugins.python.api.tree.ConditionalExpression;
import org.sonar.plugins.python.api.tree.ComprehensionExpression;
import org.sonar.plugins.python.api.tree.Tuple;

/** Python-only parser backed by the same AST implementation used by SonarPython. */
final class PythonAstParser {
  private static final Pattern TOKEN = Pattern.compile("[A-Za-z_]\\w*|\\d+(?:\\.\\d+)?|==?=?|!=?=?|:=|//|\\*\\*|[{}()\\[\\];,.+*/%<>:@-]");
  private static final Set<String> KEYWORDS = Set.of(
      "if", "else", "elif", "for", "while", "match", "case", "except", "try", "finally", "return",
      "yield", "await", "async", "def", "class", "raise", "with", "lambda", "import", "from", "in",
      "and", "or", "not", "is");

  private final Parser parser = new Parser();

  ParsedUnit parse(SourceUnit source) {
    if (source.language() != SourceUnit.Language.PYTHON) {
      throw new IllegalArgumentException("PythonAstParser only accepts Python source files");
    }
    var tree = parser.parse(source.content());
    Collector collector = new Collector(source);
    collector.collectTrivia(tree);
    tree.accept(collector);
    collector.finish();
    int logicalLines = logicalLines(source.content());
    int commentLines = collector.commentLines.size();
    int fileComplexity = collector.blocks.stream().mapToInt(CodeBlock::complexity).sum();
    return new ParsedUnit(source, source.content(), List.copyOf(collector.comments), List.copyOf(collector.blocks),
        collector.identifierProfiles(),
        Set.copyOf(collector.imports), Map.copyOf(collector.importLines), Map.copyOf(collector.aliases), copySetMap(collector.configurationKeys),
        List.copyOf(collector.calls),
        List.copyOf(collector.findings), logicalLines, commentLines, Math.max(1, fileComplexity));
  }

  private static Map<String, Set<String>> copySetMap(Map<String, Set<String>> source) {
    Map<String, Set<String>> copy = new LinkedHashMap<>();
    source.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
    return Map.copyOf(copy);
  }

  private static final class Collector extends BaseTreeVisitor {
    private final SourceUnit source;
    private final List<String> lines;
    private final Set<String> imports = new LinkedHashSet<>();
    private final Map<String, Integer> importLines = new LinkedHashMap<>();
    private final Map<String, String> aliases = new LinkedHashMap<>();
    private final Map<String, Set<String>> configurationKeys = new LinkedHashMap<>();
    private final List<RawCall> rawCalls = new ArrayList<>();
    private final List<PythonCall> calls = new ArrayList<>();
    private final List<PythonFinding> findings = new ArrayList<>();
    private final List<CodeBlock> blocks = new ArrayList<>();
    private final Map<String, MutableIdentifier> assignedIdentifiers = new LinkedHashMap<>();
    private final List<ClassIdentifier> classIdentifiers = new ArrayList<>();
    private final List<String> comments = new ArrayList<>();
    private final Map<Integer, String> commentsByLine = new HashMap<>();
    private final Set<Integer> commentLines = new HashSet<>();

    private Collector(SourceUnit source) {
      this.source = source;
      this.lines = Arrays.asList(source.content().split("\\R", -1));
    }

    @Override
    public void visitImportName(ImportName node) {
      for (AliasedName module : node.modules()) {
        String qualified = dotted(module);
        imports.add(qualified);
        importLines.putIfAbsent(qualified, module.firstToken().line());
        String local = module.alias() == null ? qualified.split("\\.")[0] : module.alias().name();
        aliases.put(local, qualified);
      }
      super.visitImportName(node);
    }

    @Override
    public void visitImportFrom(ImportFrom node) {
      String prefix = node.dottedPrefixForModule().stream().map(Token::value).reduce("", String::concat);
      String module = prefix + (node.module() == null ? "" : dotted(node.module().names()));
      if (!module.isBlank()) imports.add(module);
      if (!module.isBlank()) importLines.putIfAbsent(module, node.firstToken().line());
      if (node.isWildcardImport()) {
        findings.add(new PythonFinding("wildcard-import", node.firstToken().line(), module + ".*"));
      }
      for (AliasedName imported : node.importedNames()) {
        String member = dotted(imported);
        String qualified = module.isBlank() ? member : module + "." + member;
        String local = imported.alias() == null ? lastSegment(member) : imported.alias().name();
        aliases.put(local, qualified);
      }
      super.visitImportFrom(node);
    }

    @Override
    public void visitFunctionDef(FunctionDef node) {
      if (node.parameters() != null) {
        if (node.parameters().nonTuple().size() > 5) {
          findings.add(new PythonFinding("long-parameter-list", node.firstToken().line(),
              node.name().name() + " parameters=" + node.parameters().nonTuple().size()));
        }
        for (Parameter parameter : node.parameters().nonTuple()) {
          Expression defaultValue = parameter.defaultValue();
          if (defaultValue instanceof ListLiteral || defaultValue instanceof DictionaryLiteral || defaultValue instanceof SetLiteral) {
            findings.add(new PythonFinding("mutable-default", parameter.firstToken().line(), parameter.name().name()));
          }
        }
      }
      int functionLines = node.lastToken().line() - node.firstToken().line() + 1;
      if (functionLines > 30) {
        findings.add(new PythonFinding("long-method", node.firstToken().line(),
            node.name().name() + " lines=" + functionLines));
      }
      blocks.add(block(node));
      super.visitFunctionDef(node);
    }

    @Override
    public void visitClassDef(ClassDef node) {
      long methods = node.body().statements().stream().filter(FunctionDef.class::isInstance).count();
      int lines = node.lastToken().line() - node.firstToken().line() + 1;
      classIdentifiers.add(new ClassIdentifier(node.name().name(), enclosingScope(node),
          node.firstToken().line(), node.lastToken().line()));
      if (lines > 200 || methods > 15) {
        findings.add(new PythonFinding("large-class", node.firstToken().line(),
            node.name().name() + " lines=" + lines + ", methods=" + methods));
      }
      super.visitClassDef(node);
    }

    @Override
    public void visitQualifiedExpression(QualifiedExpression node) {
      if (!(node.parent() instanceof QualifiedExpression) && qualifiedDepth(node) > 4) {
        findings.add(new PythonFinding("long-message-chain", node.firstToken().line(), sourceText(node)));
      }
      super.visitQualifiedExpression(node);
    }

    @Override
    public void visitLambda(LambdaExpression node) {
      if (sourceText(node).length() > 80) {
        findings.add(new PythonFinding("long-lambda", node.firstToken().line(), "characters=" + sourceText(node).length()));
      }
      super.visitLambda(node);
    }

    @Override
    public void visitConditionalExpression(ConditionalExpression node) {
      if (sourceText(node).length() > 80) {
        findings.add(new PythonFinding("long-ternary", node.firstToken().line(), "characters=" + sourceText(node).length()));
      }
      super.visitConditionalExpression(node);
    }

    @Override
    public void visitPyListOrSetCompExpression(ComprehensionExpression node) {
      if (comprehensionComplexity(node) > 3) {
        findings.add(new PythonFinding("complex-comprehension", node.firstToken().line(),
            "generators/filters=" + comprehensionComplexity(node)));
      }
      super.visitPyListOrSetCompExpression(node);
    }

    @Override
    public void visitListLiteral(ListLiteral node) {
      detectNestedContainer(node);
      super.visitListLiteral(node);
    }

    @Override
    public void visitDictionaryLiteral(DictionaryLiteral node) {
      detectNestedContainer(node);
      super.visitDictionaryLiteral(node);
    }

    @Override
    public void visitSetLiteral(SetLiteral node) {
      detectNestedContainer(node);
      super.visitSetLiteral(node);
    }

    @Override
    public void visitTuple(Tuple node) {
      detectNestedContainer(node);
      super.visitTuple(node);
    }

    @Override
    public void visitAssignmentStatement(AssignmentStatement node) {
      if (node.assignedValue() instanceof DictionaryLiteral dictionary && node.lhsExpressions().size() == 1
          && node.lhsExpressions().get(0).expressions().size() == 1
          && node.lhsExpressions().get(0).expressions().get(0) instanceof Name target) {
        Set<String> keys = new LinkedHashSet<>();
        dictionary.elements().forEach(element -> {
          if (element instanceof KeyValuePair pair) {
            String key = literalKey(pair.key());
            if (key != null) keys.add(key);
          }
        });
        if (!keys.isEmpty()) configurationKeys.put(target.name(), keys);
      }
      Map<String, Integer> context = astContext(node.assignedValue());
      for (var expressionList : node.lhsExpressions()) {
        for (Expression expression : expressionList.expressions()) {
          if (expression instanceof Name target) {
            String scope = enclosingScope(node);
            String key = scope + "\u0000" + target.name();
            assignedIdentifiers.computeIfAbsent(key, ignored ->
                new MutableIdentifier(target.name(), scope, target.firstToken().line(), node.lastToken().line()))
                .merge(context);
          }
        }
      }
      super.visitAssignmentStatement(node);
    }

    @Override
    public void visitEllipsis(EllipsisExpression node) {
      findings.add(new PythonFinding("placeholder", node.firstToken().line(), "ellipsis"));
      super.visitEllipsis(node);
    }

    @Override
    public void visitRaiseStatement(RaiseStatement node) {
      if (!node.expressions().isEmpty() && "NotImplementedError".equals(expressionName(node.expressions().get(0)))) {
        findings.add(new PythonFinding("placeholder", node.firstToken().line(), "NotImplementedError"));
      }
      super.visitRaiseStatement(node);
    }

    @Override
    public void visitExceptClause(ExceptClause node) {
      String exception = expressionName(node.exception());
      if (node.exception() == null || "Exception".equals(exception) || "BaseException".equals(exception)
          || "builtins.Exception".equals(exception) || "builtins.BaseException".equals(exception)) {
        findings.add(new PythonFinding("broad-except", node.exceptKeyword().line(), exception == null ? "bare except" : exception));
      }
      if (node.body().statements().size() == 1 && node.body().statements().get(0) instanceof PassStatement) {
        findings.add(new PythonFinding("swallowed-exception", node.exceptKeyword().line(), "exception handler contains only pass"));
      }
      super.visitExceptClause(node);
    }

    @Override
    public void visitPassStatement(PassStatement node) {
      findings.add(new PythonFinding("placeholder", node.firstToken().line(), "pass"));
      super.visitPassStatement(node);
    }

    @Override
    public void visitCallExpression(CallExpression node) {
      String callee = expressionName(node.callee());
      List<String> positional = new ArrayList<>();
      Map<String, String> keywords = new LinkedHashMap<>();
      Set<String> expansions = new LinkedHashSet<>();
      for (Argument argument : node.arguments()) {
        if (argument instanceof RegularArgument regular) {
          if (regular.keywordArgument() != null) {
            keywords.put(regular.keywordArgument().name(), sourceText(regular.expression()));
          }
          else positional.add(sourceText(regular.expression()));
        } else if (argument instanceof UnpackingExpression unpacking && "**".equals(unpacking.starToken().value())) {
          String name = expressionName(unpacking.expression());
          expansions.add(name == null ? "<dynamic>" : name);
        } else {
          positional.add(sourceText(argument));
        }
      }
      rawCalls.add(new RawCall(callee == null ? "<dynamic>" : callee, node.firstToken().line(), positional,
          Map.copyOf(keywords), Set.copyOf(expansions)));
      super.visitCallExpression(node);
    }

    private void collectTrivia(Tree tree) {
      Set<String> visited = new HashSet<>();
      collectTrivia(tree, visited);
    }

    private void collectTrivia(Tree tree, Set<String> visited) {
      Token token = tree.firstToken();
      if (token != null && visited.add(token.line() + ":" + token.column() + ":" + token.value())) {
        token.trivia().forEach(trivia -> {
        String value = trivia.value().strip();
        if (!value.startsWith("#")) return;
        int line = trivia.token().line();
        String comment = value.substring(1).strip().toLowerCase(Locale.ROOT);
        comments.add(comment);
        commentsByLine.merge(line, comment, (left, right) -> left + " " + right);
        commentLines.add(line);
        if (comment.matches(".*\\b(?:todo|fixme|hack|xxx)\\b.*")) {
          findings.add(new PythonFinding("placeholder", line, "unfinished-work marker"));
        }
        });
      }
      for (Tree child : tree.children()) collectTrivia(child, visited);
    }

    private void finish() {
      for (RawCall raw : rawCalls) {
        String qualified = resolveAlias(raw.name);
        Set<String> resolvedKeys = new LinkedHashSet<>();
        raw.expansions.forEach(name -> resolvedKeys.addAll(configurationKeys.getOrDefault(name, Set.of())));
        PythonCall call = new PythonCall(qualified, raw.line, List.copyOf(raw.positional), Map.copyOf(raw.keywords),
            Set.copyOf(raw.expansions), Set.copyOf(resolvedKeys));
        calls.add(call);
        if ("print".equals(call.simpleName())) findings.add(new PythonFinding("debug-output", call.line(), qualified));
        if ("eval".equals(call.simpleName()) || "exec".equals(call.simpleName())) {
          findings.add(new PythonFinding("unsafe-eval", call.line(), qualified));
        }
        String simple = call.simpleName().toLowerCase(Locale.ROOT);
        if ((simple.equals("fit") || simple.equals("fit_transform"))
            && call.positionalArguments().stream().anyMatch(PythonAstParser::looksLikeEvaluationData)) {
          findings.add(new PythonFinding("training-on-evaluation-data", call.line(), qualified));
        }
      }
    }

    private List<IdentifierProfile> identifierProfiles() {
      List<IdentifierProfile> result = new ArrayList<>();
      for (CodeBlock block : blocks) {
        result.add(new IdentifierProfile(block.file(), block.scope(), block.name(), "function",
            block.startLine(), block.endLine(), functionContext(block)));
      }
      for (MutableIdentifier identifier : assignedIdentifiers.values()) {
        if (!identifier.features.isEmpty()) {
          result.add(new IdentifierProfile(source.path(), identifier.scope, identifier.name, "variable",
              identifier.startLine, identifier.endLine, Map.copyOf(identifier.features)));
        }
      }
      for (ClassIdentifier identifier : classIdentifiers) {
        Map<String, Integer> features = new HashMap<>();
        String qualified = "<module>".equals(identifier.scope) ? identifier.name : identifier.scope + "." + identifier.name;
        blocks.stream().filter(block -> block.scope().equals(qualified) || block.scope().startsWith(qualified + "."))
            .map(Collector::functionContext).forEach(vector -> mergeFeatures(features, vector));
        if (!features.isEmpty()) {
          result.add(new IdentifierProfile(source.path(), identifier.scope, identifier.name, "class",
              identifier.startLine, identifier.endLine, Map.copyOf(features)));
        }
      }
      return List.copyOf(result);
    }

    private static Map<String, Integer> functionContext(CodeBlock block) {
      Map<String, Integer> features = new HashMap<>();
      block.behavior().forEach((key, value) -> features.put("behavior:" + key, value));
      block.stylePatterns().forEach((key, value) -> features.put("pattern:" + key, value));
      block.callSequence().forEach(call -> features.merge("call-family:" + semanticCallFamily(call), 1, Integer::sum));
      features.put("complexity", block.complexity());
      features.put("nesting", block.maxNesting());
      features.put("parameters", block.parameters().size());
      return Map.copyOf(features);
    }

    private static Map<String, Integer> astContext(Tree tree) {
      Map<String, Integer> features = new HashMap<>();
      collectAstContext(tree, features);
      return Map.copyOf(features);
    }

    private static void collectAstContext(Tree tree, Map<String, Integer> features) {
      if (tree == null) return;
      String type = tree.getClass().getSimpleName().replace("Impl", "");
      if (!(tree instanceof Name) && !(tree instanceof Token)) {
        features.merge("ast:" + type, 1, Integer::sum);
      }
      if (tree instanceof CallExpression call) {
        String name = expressionName(call.callee());
        features.merge("call-family:" + semanticCallFamily(name == null ? "<dynamic>" : name), 1, Integer::sum);
      } else if (tree instanceof BinaryExpression binary) {
        features.merge("operator:" + binary.operator().value(), 1, Integer::sum);
      }
      for (Tree child : tree.children()) collectAstContext(child, features);
    }

    private static void mergeFeatures(Map<String, Integer> target, Map<String, Integer> source) {
      source.forEach((key, value) -> target.merge(key, value, Integer::sum));
    }

    private static String semanticCallFamily(String call) {
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
      return "local-or-other";
    }

    private static String enclosingScope(Tree tree) {
      List<String> names = new ArrayList<>();
      Tree current = tree.parent();
      while (current != null) {
        if (current instanceof FunctionDef function) names.add(0, function.name().name());
        else if (current instanceof ClassDef type) names.add(0, type.name().name());
        current = current.parent();
      }
      return names.isEmpty() ? "<module>" : String.join(".", names);
    }

    private String resolveAlias(String value) {
      int dot = value.indexOf('.');
      String root = dot < 0 ? value : value.substring(0, dot);
      String resolved = aliases.get(root);
      return resolved == null ? value : resolved + (dot < 0 ? "" : value.substring(dot));
    }

    private CodeBlock block(FunctionDef function) {
      int start = function.firstToken().line();
      int end = lexicalBlockEnd(start);
      String body = String.join("\n", lines.subList(Math.max(0, start - 1), Math.min(lines.size(), end)));
      BlockFacts facts = new BlockFacts(function);
      function.body().accept(facts);
      List<String> normalized = normalizeTokens(body);
      Set<String> rawTokens = new LinkedHashSet<>();
      Matcher matcher = TOKEN.matcher(body);
      while (matcher.find()) rawTokens.add(matcher.group().toLowerCase(Locale.ROOT));
      return new CodeBlock(source.path(), function.name().name(), scopeOf(function), start, end, normalized, Set.copyOf(rawTokens),
          Map.copyOf(facts.behavior), Map.copyOf(facts.stylePatterns), facts.complexity, facts.maxNesting, nearbyComments(start, end),
          nearbyCommentLineCount(start, end),
          function.docstring() == null ? "" : function.docstring().trimmedQuotesValue(), parameters(function),
          Set.copyOf(facts.identifiers), List.copyOf(facts.callSequence), Set.copyOf(facts.returnTokens));
    }

    private static String scopeOf(FunctionDef function) {
      return enclosingScope(function);
    }

    /** Finds the last physically indented source line, avoiding a parser DEDENT token on the next definition. */
    private int lexicalBlockEnd(int start) {
      String definition = lines.get(Math.max(0, start - 1));
      int baseIndent = indentation(definition);
      int lastContent = start;
      for (int index = start; index < lines.size(); index++) {
        String line = lines.get(index);
        if (line.isBlank()) continue;
        if (indentation(line) <= baseIndent) break;
        lastContent = index + 1;
      }
      return lastContent;
    }

    private static int indentation(String line) {
      int result = 0;
      while (result < line.length() && Character.isWhitespace(line.charAt(result))) result++;
      return result;
    }

    private Set<String> parameters(FunctionDef function) {
      if (function.parameters() == null) return Set.of();
      Set<String> result = new LinkedHashSet<>();
      function.parameters().nonTuple().forEach(parameter -> result.add(parameter.name().name()));
      return Set.copyOf(result);
    }

    private String sourceText(Tree tree) {
      if (tree == null || tree.firstToken() == null || tree.lastToken() == null) return "";
      int start = Math.max(1, tree.firstToken().line());
      int end = Math.min(lines.size(), tree.lastToken().line());
      return String.join(" ", lines.subList(start - 1, end)).strip();
    }

    private void detectNestedContainer(Tree node) {
      if (!hasContainerAncestor(node.parent()) && containerDepth(node) > 3) {
        findings.add(new PythonFinding("multiply-nested-container", node.firstToken().line(),
            "nesting depth=" + containerDepth(node)));
      }
    }

    private String nearbyComments(int start, int end) {
      List<String> selected = new ArrayList<>();
      for (int line = Math.max(1, start - 3); line <= end; line++) {
        if (commentsByLine.containsKey(line)) selected.add(commentsByLine.get(line));
      }
      return String.join(" ", selected);
    }

    private int nearbyCommentLineCount(int start, int end) {
      int count = 0;
      for (int line = Math.max(1, start - 3); line <= end; line++) {
        if (commentsByLine.containsKey(line)) count++;
      }
      return count;
    }
  }

  private static final class BlockFacts extends BaseTreeVisitor {
    private int complexity = 1;
    private int nesting;
    private int maxNesting;
    private final Map<String, Integer> behavior = new HashMap<>();
    private final Map<String, Integer> stylePatterns = new HashMap<>();
    private final Set<String> identifiers = new LinkedHashSet<>();
    private final List<String> callSequence = new ArrayList<>();
    private final Set<String> returnTokens = new LinkedHashSet<>();

    private BlockFacts(FunctionDef function) {
      if (function.asyncKeyword() != null) add("async");
    }

    @Override public void visitFunctionDef(FunctionDef node) { /* Nested functions are separate blocks. */ }
    @Override public void visitIfStatement(IfStatement node) { pattern("if"); enterDecision("branch", () -> super.visitIfStatement(node)); }
    @Override public void visitForStatement(ForStatement node) { pattern("for-loop"); enterDecision("iteration", () -> super.visitForStatement(node)); }
    @Override public void visitWhileStatement(WhileStatement node) { pattern("while-loop"); enterDecision("iteration", () -> super.visitWhileStatement(node)); }
    @Override public void visitExceptClause(ExceptClause node) { pattern("except-handler"); enterDecision("error", () -> super.visitExceptClause(node)); }
    @Override public void visitComprehensionFor(ComprehensionFor node) { pattern("comprehension"); decision("iteration"); super.visitComprehensionFor(node); }
    @Override public void visitComprehensionIf(ComprehensionIf node) { pattern("comprehension-filter"); decision("branch"); super.visitComprehensionIf(node); }
    @Override public void visitTryStatement(TryStatement node) { pattern("try-except"); add("error"); super.visitTryStatement(node); }
    @Override public void visitWithStatement(WithStatement node) { pattern("context-manager"); add("resource"); super.visitWithStatement(node); }
    @Override public void visitAssignmentStatement(AssignmentStatement node) { pattern("assignment"); super.visitAssignmentStatement(node); }
    @Override public void visitBinaryExpression(BinaryExpression node) {
      String operator = node.operator().value();
      if ("and".equals(operator) || "or".equals(operator)) { pattern("boolean-chain"); complexity++; }
      super.visitBinaryExpression(node);
    }
    @Override public void visitName(Name node) { identifiers.add(node.name().toLowerCase(Locale.ROOT)); super.visitName(node); }
    @Override public void visitCallExpression(CallExpression node) {
      add("call");
      pattern("call");
      String name = expressionName(node.callee());
      callSequence.add(name == null ? "<dynamic>" : name.toLowerCase(Locale.ROOT));
      super.visitCallExpression(node);
    }
    @Override public void visitReturnStatement(ReturnStatement node) {
      add("output");
      pattern("return");
      for (Tree child : node.children()) collectNames(child, returnTokens);
      super.visitReturnStatement(node);
    }
    @Override public void visitRaiseStatement(org.sonar.plugins.python.api.tree.RaiseStatement node) { pattern("raise"); add("error"); super.visitRaiseStatement(node); }
    @Override public void visitAwaitExpression(org.sonar.plugins.python.api.tree.AwaitExpression node) { pattern("await"); add("async"); super.visitAwaitExpression(node); }

    private void decision(String category) { complexity++; add(category); }
    private void add(String category) { behavior.merge(category, 1, Integer::sum); }
    private void pattern(String category) { stylePatterns.merge(category, 1, Integer::sum); }
    private void enterDecision(String category, Runnable scan) {
      decision(category);
      nesting++;
      maxNesting = Math.max(maxNesting, nesting);
      scan.run();
      nesting--;
    }
  }

  private static void collectNames(Tree tree, Set<String> target) {
    if (tree instanceof Name name) target.add(name.name().toLowerCase(Locale.ROOT));
    for (Tree child : tree.children()) collectNames(child, target);
  }

  private static int qualifiedDepth(QualifiedExpression expression) {
    int depth = 1;
    Expression current = expression.qualifier();
    while (current instanceof QualifiedExpression qualified) {
      depth++;
      current = qualified.qualifier();
    }
    return depth;
  }

  private static int comprehensionComplexity(Tree tree) {
    int count = tree instanceof ComprehensionFor || tree instanceof ComprehensionIf ? 1 : 0;
    for (Tree child : tree.children()) count += comprehensionComplexity(child);
    return count;
  }

  private static boolean isContainer(Tree tree) {
    return tree instanceof ListLiteral || tree instanceof DictionaryLiteral || tree instanceof SetLiteral || tree instanceof Tuple;
  }

  private static int containerDepth(Tree tree) {
    int nested = 0;
    for (Tree child : tree.children()) nested = Math.max(nested, containerDepth(child));
    return (isContainer(tree) ? 1 : 0) + nested;
  }

  private static boolean hasContainerAncestor(Tree tree) {
    Tree current = tree;
    while (current != null) {
      if (isContainer(current)) return true;
      current = current.parent();
    }
    return false;
  }

  private static String literalKey(Expression expression) {
    if (expression instanceof StringLiteral literal) return literal.trimmedQuotesValue();
    if (expression instanceof Name name) return name.name();
    return null;
  }

  private static List<String> normalizeTokens(String code) {
    List<String> normalized = new ArrayList<>();
    Matcher matcher = TOKEN.matcher(code);
    while (matcher.find()) {
      String token = matcher.group();
      String lower = token.toLowerCase(Locale.ROOT);
      normalized.add(KEYWORDS.contains(lower) || !Character.isLetter(token.charAt(0)) ? lower : "ID");
    }
    return List.copyOf(normalized);
  }

  private static String expressionName(Expression expression) {
    if (expression == null) return null;
    if (expression instanceof Name name) return name.name();
    if (expression instanceof QualifiedExpression qualified) {
      String prefix = expressionName(qualified.qualifier());
      return prefix == null ? qualified.name().name() : prefix + "." + qualified.name().name();
    }
    return expression.firstToken() == null ? null : expression.firstToken().value();
  }

  private static String dotted(AliasedName value) { return dotted(value.dottedName().names()); }
  private static String dotted(List<Name> names) { return names.stream().map(Name::name).reduce((a, b) -> a + "." + b).orElse(""); }
  private static String lastSegment(String value) { int dot = value.lastIndexOf('.'); return dot < 0 ? value : value.substring(dot + 1); }

  private static int logicalLines(String code) {
    int count = 0;
    for (String line : code.split("\\R")) {
      String stripped = line.strip();
      if (!stripped.isEmpty() && !stripped.startsWith("#")) count++;
    }
    return count;
  }

  private static boolean looksLikeEvaluationData(String expression) {
    String normalized = expression.toLowerCase(Locale.ROOT);
    return normalized.matches(".*(?:^|[^a-z0-9])(?:x_?test|y_?test|test_?data|validation_?data|val_?data)(?:[^a-z0-9]|$).*");
  }

  private record RawCall(String name, int line, List<String> positional, Map<String, String> keywords, Set<String> expansions) {}

  private static final class MutableIdentifier {
    private final String name;
    private final String scope;
    private final int startLine;
    private final int endLine;
    private final Map<String, Integer> features = new HashMap<>();

    private MutableIdentifier(String name, String scope, int startLine, int endLine) {
      this.name = name;
      this.scope = scope;
      this.startLine = startLine;
      this.endLine = endLine;
    }

    private void merge(Map<String, Integer> context) {
      Collector.mergeFeatures(features, context);
    }
  }

  private record ClassIdentifier(String name, String scope, int startLine, int endLine) {}
}
