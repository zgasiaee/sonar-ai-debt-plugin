package io.github.aidebt.core;

import java.util.Map;

/** A declared Python identifier and its deterministic AST-context embedding. */
record IdentifierProfile(
    String file,
    String scope,
    String name,
    String kind,
    int startLine,
    int endLine,
    Map<String, Integer> semanticFeatures) {}
