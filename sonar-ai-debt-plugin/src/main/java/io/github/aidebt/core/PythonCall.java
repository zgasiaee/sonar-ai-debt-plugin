package io.github.aidebt.core;

import java.util.Map;
import java.util.Set;
import java.util.List;

record PythonCall(
    String qualifiedName,
    int line,
    List<String> positionalArguments,
    Map<String, String> keywordArguments,
    Set<String> configurationExpansions,
    Set<String> resolvedConfigurationKeys) {

  String simpleName() {
    int separator = qualifiedName.lastIndexOf('.');
    return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
  }

  boolean hasExplicitConfiguration() {
    return !positionalArguments.isEmpty() || !keywordArguments.isEmpty() || configurationExpansion();
  }

  boolean hasDirectArguments() { return !positionalArguments.isEmpty() || !keywordArguments.isEmpty(); }

  boolean configurationExpansion() { return !configurationExpansions.isEmpty(); }
  boolean unresolvedConfigurationExpansion() {
    return configurationExpansion() && resolvedConfigurationKeys.isEmpty();
  }

  Set<String> effectiveKeywordArguments() {
    java.util.HashSet<String> keys = new java.util.HashSet<>(keywordArguments.keySet());
    keys.addAll(resolvedConfigurationKeys);
    return Set.copyOf(keys);
  }
}
