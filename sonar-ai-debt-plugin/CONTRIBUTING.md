# Contributing

Use Java 17 and Maven 3.9 or newer. Keep the analysis core independent of SonarQube APIs, add unit tests for every formula or threshold change, and document any construct-validity assumption in `docs/METRICS.md`.

Before opening a pull request, run:

```bash
mvn clean verify
```

Do not commit generated `target/` content, downloaded models, experiment datasets, or credentials. New smell rules require a stable identifier, a precise rationale, positive and negative fixtures, and evidence from a blinded validation sample.
