package io.github.aidebt.research;

import io.github.aidebt.core.AnalysisConfig;
import io.github.aidebt.core.CalibrationCollector;
import io.github.aidebt.core.ProjectAnalyzer;
import io.github.aidebt.core.SourceUnit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Standalone research utility for exporting calibration candidates without a SonarQube server.
 *
 * <p>The manifest is tab-separated: {@code group_id<TAB>unit_id<TAB>source_path}. Group IDs
 * define validation clusters; unit IDs distinguish independently analyzed alternatives without
 * revealing provenance.
 */
public final class CalibrationExportCli {
  private CalibrationExportCli() {}

  public static void main(String[] arguments) throws IOException {
    Path manifest = option(arguments, "--manifest");
    Path output = option(arguments, "--output");
    List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
    Path parent = output.toAbsolutePath().normalize().getParent();
    if (parent != null) Files.createDirectories(parent);
    Files.writeString(output, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
    int units = 0;
    int candidates = 0;
    for (int index = 0; index < lines.size(); index++) {
      String line = lines.get(index).strip();
      if (line.isEmpty() || line.startsWith("#")) continue;
      String[] fields = line.split("\\t", -1);
      if (fields.length != 3 || fields[0].isBlank() || fields[1].isBlank() || fields[2].isBlank()) {
        throw new IllegalArgumentException("Invalid manifest line " + (index + 1)
            + "; expected group_id<TAB>unit_id<TAB>source_path");
      }
      Path sourcePath = manifest.toAbsolutePath().normalize().getParent().resolve(fields[2]).normalize();
      List<SourceUnit> sources = loadSources(sourcePath);
      if (sources.isEmpty()) continue;
      CalibrationCollector collector = CalibrationCollector.enabled(fields[0], fields[1]);
      new ProjectAnalyzer(AnalysisConfig.defaults(), collector).analyzeWithoutSpecDetect(sources);
      Files.writeString(output, collector.jsonLines(), StandardCharsets.UTF_8,
          StandardOpenOption.APPEND);
      units++;
      candidates += collector.size();
    }
    System.out.printf("Exported %d candidates from %d independent units to %s%n",
        candidates, units, output);
  }

  private static List<SourceUnit> loadSources(Path input) throws IOException {
    if (!Files.exists(input)) throw new IllegalArgumentException("Source path does not exist: " + input);
    List<Path> paths;
    if (Files.isRegularFile(input)) {
      paths = List.of(input);
    } else {
      try (var stream = Files.walk(input)) {
        paths = stream.filter(Files::isRegularFile)
            .filter(path -> SourceUnit.supports(path.getFileName().toString()))
            .filter(path -> !excluded(path))
            .sorted(Comparator.comparing(Path::toString))
            .toList();
      }
    }
    List<SourceUnit> sources = new ArrayList<>();
    for (Path path : paths) {
      String alias = Files.isRegularFile(input)
          ? "source/" + path.getFileName()
          : "source/" + input.relativize(path).toString().replace('\\', '/');
      sources.add(new SourceUnit(alias, Files.readString(path, StandardCharsets.UTF_8),
          SourceUnit.Language.PYTHON));
    }
    return sources;
  }

  private static boolean excluded(Path path) {
    String normalized = path.toString().replace('\\', '/');
    return normalized.contains("/.git/") || normalized.contains("/.venv/")
        || normalized.contains("/venv/") || normalized.contains("/node_modules/")
        || normalized.contains("/__pycache__/");
  }

  private static Path option(String[] arguments, String name) {
    for (int index = 0; index + 1 < arguments.length; index++) {
      if (arguments[index].equals(name)) return Path.of(arguments[index + 1]);
    }
    throw new IllegalArgumentException("Missing required option " + name);
  }
}
