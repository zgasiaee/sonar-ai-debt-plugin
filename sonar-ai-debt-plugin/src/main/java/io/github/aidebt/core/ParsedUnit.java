package io.github.aidebt.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

record ParsedUnit(
    SourceUnit source,
    String codeWithoutCommentsAndStrings,
    List<String> comments,
    List<CodeBlock> blocks,
    List<IdentifierProfile> identifiers,
    Set<String> imports,
    Map<String, Integer> importLines,
    Map<String, String> aliases,
    Map<String, Set<String>> configurationKeys,
    List<PythonCall> calls,
    List<PythonFinding> findings,
    int logicalLines,
    int commentLines,
    int fileComplexity) {}
