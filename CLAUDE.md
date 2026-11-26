# CLAUDE.md — jenkins-shared-library

## Project Overview

Jenkins shared library for hardware test automation at Analog Devices. Orchestrates testing of FPGA/SDR boards (Zynq, Pluto, ADRV9009, etc.) using nebula CLI for board management, Docker-containerized test execution, and Elasticsearch telemetry. The library follows the [Jenkins shared library structure](https://www.jenkins.io/doc/book/pipeline/shared-libraries/).

## Repository Layout

```
src/sdg/              Groovy classes (package: sdg)
  Gauntlet.groovy       Core pipeline orchestrator (~2000 lines, script not class)
  Logger.groovy         Level-aware logger (1=error, 2=warning, 3=info)
  NominalException.groovy  Non-fatal error for stopping a single board
  StepExecutor.groovy   Jenkins step wrapper (implements IStepExecutor)
  ioc/                  IoC container for testability
    ContextRegistry.groovy  Static singleton context registry
    DefaultContext.groovy   Production Jenkins context
  stages/               Stage implementations (all implement IStage)
vars/                 Global pipeline variables (Jenkins convention)
  getGauntlet.groovy    Factory: creates and returns configured Gauntlet
  getGauntEnv.groovy    Default configuration map (~100 fields)
  getStage.groovy       Dynamic class loader for stage classes
test/sdg/stages/      Spock test specs (one per stage class)
doc/source/           Sphinx documentation (RST + PlantUML diagrams)
```

## Build & Test

Gradle is the build system. No wrapper is checked in — CI generates it. Locally, use an installed Gradle or generate the wrapper first.

The build requires a full **JDK 17** (with `javac`), not just a JRE — build.gradle compiles with the JVM that runs Gradle.

```bash
# Install JRE + JDK 17 (Debian/Ubuntu; needs sudo)
sudo apt-get update && sudo apt-get install -y openjdk-17-jre openjdk-17-jdk

# No sudo? Install Eclipse Temurin JDK 17 locally into your home directory
curl -fsSL -o /tmp/jdk17.tar.gz \
  "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
mkdir -p "$HOME/.local/jdk" && tar -xzf /tmp/jdk17.tar.gz -C "$HOME/.local/jdk"
export JAVA_HOME="$HOME/.local/jdk/$(ls "$HOME/.local/jdk")"
export PATH="$JAVA_HOME/bin:$PATH"

# Generate wrapper if needed
gradle wrapper --gradle-version 9.0.0

# Run tests
./gradlew test

# Run tests with coverage report (HTML output in build/reports/jacoco/)
./gradlew test jacocoTestReport

# Generate GroovyDoc
./gradlew groovydoc
```

- **Language**: Groovy 3.0.9, JDK 17
- **Test framework**: Spock 2.0 with JUnit Platform
- **Coverage**: JaCoCo (XML + HTML reports)

## Testing Conventions

- Tests live in `test/sdg/stages/`, named `Test<StageName>.groovy`
- All test classes extend `spock.lang.Specification`
- Tests mock `IStepExecutor` and `IContext` via the IoC container (`ContextRegistry`)
- Tests parse `vars/getGauntEnv.groovy` with `GroovyShell` to get default config
- Use Spock BDD blocks: `given:`/`when:`/`then:` or `given:`/`expect:`
- Test fixtures in `test/resources/nebula_failures/`

## Architecture

- **IoC pattern**: `ContextRegistry` → `IContext` → `IStepExecutor` enables unit testing by injecting mock Jenkins steps
- **Stage pattern**: Each stage implements `IStage` (methods: `getStageName()`, `getCls()`). The closure from `getCls()` receives `(gauntlet, board)` and delegates to `stageSteps()`
- **Dynamic loading**: `getStage.groovy` uses `GroovyClassLoader` to instantiate stages by class name
- **Gauntlet.groovy** is a Groovy script (not a class) — uses script-level field declarations

## Active Refactoring

The `refactor-2` branch is extracting inline stage logic from `Gauntlet.groovy`'s `old_stage_library()` (large switch/case) into individual `IStage` classes under `src/sdg/stages/`. The new path uses `stage_library()` → `getStage()`. Both patterns coexist during the transition.

## Naming Conventions

- Interfaces: `I` prefix (`IStage`, `IStepExecutor`, `IContext`)
- Stage classes: PascalCase matching stage name (`UpdateBOOTFiles`, `LinuxTests`)
- Test classes: `Test` prefix (`TestUpdateBOOTFiles`, `TestLinuxTests`)
- vars scripts: mixed camelCase and snake_case (`getGauntlet.groovy`, `check_for_box.groovy`)

## CI/CD

- **GitHub Actions** (primary): `.github/workflows/build.yml` runs Gradle tests + coverage on push/PR to `master` and `refactor-2`
- **Documentation**: `.github/workflows/doc.yml` builds Sphinx docs, deploys to GitHub Pages
- **Branches**: `master` (main), `refactor-2` (active development)

## Documentation

Sphinx docs in `doc/source/` using furo theme with PlantUML and Mermaid extensions. Build with:

```bash
pip install -r requirements_doc.txt
cd doc && make html
```

## Key Dependencies

| Dependency | Purpose |
|---|---|
| `groovy-cps` | CloudBees CPS transform for Jenkins pipelines |
| `jenkins-core` | Jenkins API access |
| `pipeline-model-definition` | Declarative pipeline support |
| `jenkins-pipeline-unit` | Pipeline testing without Jenkins |
| `spock-core` | BDD test framework |
