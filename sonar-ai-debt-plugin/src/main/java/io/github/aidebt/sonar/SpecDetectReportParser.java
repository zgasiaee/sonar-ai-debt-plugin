package io.github.aidebt.sonar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.aidebt.core.DebtFinding;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts the native SpecDetect4AI JSON report into source-located AISD evidence. */
final class SpecDetectReportParser {
  private static final Pattern LINE = Pattern.compile("(?i)\\bline\\D{0,12}(\\d+)");

  List<DebtFinding> parse(Path report, Path baseDirectory) throws IOException {
    try (Reader reader = Files.newBufferedReader(report, StandardCharsets.UTF_8)) {
      JsonElement root = JsonParser.parseReader(reader);
      if (!root.isJsonObject()) throw new IOException("SpecDetect4AI report root must be a JSON object");
      List<DebtFinding> findings = new ArrayList<>();
      Map<String, List<String>> sourceCache = new HashMap<>();
      for (var fileEntry : root.getAsJsonObject().entrySet()) {
        if (!fileEntry.getValue().isJsonObject()) continue;
        String file = normalizedPath(fileEntry.getKey(), baseDirectory);
        JsonObject rules = fileEntry.getValue().getAsJsonObject();
        for (var ruleEntry : rules.entrySet()) {
          String ruleId = ruleEntry.getKey();
          if (!isSupportedRule(ruleId) || !ruleEntry.getValue().isJsonArray()) continue;
          for (JsonElement messageElement : ruleEntry.getValue().getAsJsonArray()) {
            if (!messageElement.isJsonPrimitive()) continue;
            String message = messageElement.getAsString();
            int line = lineOf(message);
            findings.add(new DebtFinding(
                file, line, ruleKey(ruleId), message, sourceLine(file, line, sourceCache)));
          }
        }
      }
      return List.copyOf(findings);
    } catch (RuntimeException error) {
      throw new IOException("Invalid SpecDetect4AI JSON report: " + error.getMessage(), error);
    }
  }

  private static int lineOf(String message) {
    Matcher matcher = LINE.matcher(message);
    return matcher.find() ? Math.max(1, Integer.parseInt(matcher.group(1))) : 1;
  }

  private static String sourceLine(
      String file, int line, Map<String, List<String>> sourceCache) {
    try {
      List<String> lines = sourceCache.computeIfAbsent(file, key -> {
        try {
          return Files.readAllLines(Path.of(key), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
          return List.of();
        }
      });
      return line > 0 && line <= lines.size() ? lines.get(line - 1).strip() : "";
    } catch (RuntimeException ignored) {
      return "";
    }
  }

  private static String normalizedPath(String value, Path baseDirectory) {
    Path path = Path.of(value);
    if (!path.isAbsolute()) {
      Path fromBase = baseDirectory.resolve(path).normalize();
      Path fromParent = baseDirectory.getParent() == null ? fromBase : baseDirectory.getParent().resolve(path).normalize();
      path = Files.exists(fromBase) || !Files.exists(fromParent) ? fromBase : fromParent;
    }
    return path.normalize().toAbsolutePath().toString();
  }

  static String ruleKey(String ruleId) {
    return "specdetect-" + ruleId.toLowerCase(Locale.ROOT);
  }

  private static boolean isSupportedRule(String ruleId) {
    return ruleId.matches("R(?:[1-9]|1[0-9]|2[0-4])") || "R11bis".equalsIgnoreCase(ruleId);
  }
}
