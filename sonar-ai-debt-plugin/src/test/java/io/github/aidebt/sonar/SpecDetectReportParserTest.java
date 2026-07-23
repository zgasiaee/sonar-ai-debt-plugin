package io.github.aidebt.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpecDetectReportParserTest {
  @TempDir Path temporaryDirectory;

  @Test
  void preservesRuleIdentityFileAndLine() throws Exception {
    Path source = temporaryDirectory.resolve("src/model.py");
    Files.createDirectories(source.getParent());
    Files.writeString(source, "x = 1\n");
    Path report = temporaryDirectory.resolve("specDetect4ai_results.json");
    Files.writeString(report, """
        {
          "src/model.py": {
            "R2": ["Random Seed Not Set at line 7"],
            "R11bis": ["Potential data leakage without a pipeline at line 19"]
          }
        }
        """);

    var findings = new SpecDetectReportParser().parse(report, temporaryDirectory);

    assertEquals(2, findings.size());
    assertEquals(source.toAbsolutePath().normalize().toString(), findings.get(0).file());
    assertEquals("specdetect-r2", findings.get(0).rule());
    assertEquals(7, findings.get(0).line());
    assertEquals("specdetect-r11bis", findings.get(1).rule());
    assertEquals(19, findings.get(1).line());
  }

  @Test
  void acceptsPathsPrefixedWithTheScannerDirectoryName() throws Exception {
    Path project = temporaryDirectory.resolve("final sample");
    Path source = project.resolve("src/model.py");
    Files.createDirectories(source.getParent());
    Files.writeString(source, "x = 1\n");
    Path report = project.resolve("specDetect4ai_results.json");
    Files.writeString(report, """
        {"final sample/src/model.py": {"R5": ["Hyperparameter not explicitly set at line 1"]}}
        """);

    var finding = new SpecDetectReportParser().parse(report, project).get(0);

    assertEquals(source.toAbsolutePath().normalize().toString(), finding.file());
  }

  @Test
  void recommendationUsesTheFlaggedSourceExpression() throws Exception {
    Path source = temporaryDirectory.resolve("src/data.py");
    Files.createDirectories(source.getParent());
    Files.writeString(source, """
        import pandas as pd
        frame = pd.read_csv(path)
        values = frame.values
        """);
    Path report = temporaryDirectory.resolve("specDetect4ai_results.json");
    Files.writeString(report, """
        {
          "src/data.py": {
            "R14": ["DataFrame.values used at line 3"],
            "R21": ["Read schema not explicitly set at line 2"]
          }
        }
        """);

    var findings = new SpecDetectReportParser().parse(report, temporaryDirectory);

    assertEquals("values = frame.values", findings.get(0).sourceLine());
    assertTrue(findings.get(0).message().contains("frame.to_numpy(...)"));
    assertTrue(findings.get(1).message().contains("pd.read_csv(...)"));
    assertTrue(findings.get(1).message().contains("dtype={column: dtype, ...}"));
  }
}
