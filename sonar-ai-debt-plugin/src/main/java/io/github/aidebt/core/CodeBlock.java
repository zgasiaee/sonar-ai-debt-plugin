package io.github.aidebt.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

record CodeBlock(
    String file,
    String name,
    String scope,
    int startLine,
    int endLine,
    List<String> normalizedTokens,
    Set<String> rawTokenSet,
    Map<String, Integer> behavior,
    Map<String, Integer> stylePatterns,
    int complexity,
    int maxNesting,
    String nearbyComments,
    int nearbyCommentLines,
    String docstring,
    Set<String> parameters,
    Set<String> identifiers,
    List<String> callSequence,
    Set<String> returnTokens) {}
