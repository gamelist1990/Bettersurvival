# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.


# Bettersurvival — Dev Guide (CLAUDE.md)

## Overview
This repository is a Minecraft Paper/Spigot plugin written in Java. It uses Gradle with Paper dev plugins for local development and testing.

- Language: Java
  - Build target: Java 21 toolchain in Gradle (see build script)
  - Recommended JDK: Java 21 (Gradle toolchain uses 21). Note: GitHub Action uses JDK 17; reconcile per CI requirements.

- Build tool: Gradle (wrapper included)
  - Files: c:\Users\PC_User\IdeaProjects\bettersurvival\build.gradle
  - Wrapper scripts: c:\Users\PC_User\IdeaProjects\bettersurvival\gradlew (Unix), gradlew.bat (Windows)

- Runtime platform: PaperMC / Spigot (Minecraft 1.21.10)
  - Paper APIs are compileOnly; runServer uses a dev bundle.

## Quick commands
(Use repository wrapper scripts)

- Build the plugin
  - Windows:
    - Bash snippet (Windows Git Bash / WSL):
      ```bash
      ./gradlew.bat build
      ```
    - Output JAR: build/libs/Bettersurvival-<version>.jar
  - macOS/Linux:
    ```bash
    ./gradlew build
    ```

- Run a local Paper dev server
  - Windows:
    ```bash
    ./gradlew.bat runServer
    ```
  - macOS/Linux:
    ```bash
    ./gradlew runServer
    ```
  - See runServer configuration in: c:\Users\PC_User\IdeaProjects\bettersurvival\build.gradle:32-38

- Manual plugin testing
  - Copy the built JAR to the dev server plugin folder:
    - JAR target: c:\Users\PC_User\IdeaProjects\bettersurvival\build\libs\<jar>.jar
    - Put it in: c:\Users\PC_User\IdeaProjects\bettersurvival\run\plugins\ (e.g., run/plugins/Bettersurvival/)
    - Restart the dev server or use server reload.

- Run logs
  - Dev server logs live under: c:\Users\PC_User\IdeaProjects\bettersurvival\run\logs\ (see latest/*.log.gz)

## How to run a single test
There are currently no tests in the repository (no src/test folder found). If you add tests, use the Gradle `test` task and the Gradle test filtering feature:

- Run all tests:
  ```bash
  ./gradlew test
  ```
- Run a single test class or method:
  ```bash
  # Run a single test class
  ./gradlew test --tests "org.pexserver.koukunn.bettersurvival.YourTestClass"

  # Run a single method within a class
  ./gradlew test --tests "org.pexserver.koukunn.bettersurvival.YourTestClass.yourTestMethod"
  ```
- Note: Add JUnit 5 (jupiter) or your test framework in build.gradle dependencies if you plan to write tests (e.g., `testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'`).

## Lint / Static analysis
- No linter plugins configured (e.g., Spotless, Checkstyle, PMD). To add linting, integrate Spotless or Checkstyle in build.gradle.
- The GitHub workflow (c:\Users\PC_User\IdeaProjects\bettersurvival\.github\workflows\release.yml) only builds and publishes the JAR.

## Dev / Build Dependencies
From c:\Users\PC_User\IdeaProjects\bettersurvival\build.gradle:23-30

- Build / Run plugins:
  - xyz.jpenilla.run-paper plugin v2.3.1 — adds `runServer` task for Paper dev
  - io.papermc.paperweight.userdev plugin v2.0.0-beta.19 — paper dev bundle

- Maven repositories:
  - mavenCentral()
  - papermc repo: https://repo.papermc.io/repository/maven-public/
  - Floodgate repo: https://repo.opencollab.dev/main/

- Key dependencies:
  - compileOnly io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT (Paper API, compile-time only)
  - implementation com.google.code.gson:gson:2.10.1
  - compileOnly org.geysermc.floodgate:api:2.2.4-SNAPSHOT (floodgate)
  - compileOnly org.geysermc.cumulus:cumulus:1.0.18 (UI forms)
  - compileOnly org.geysermc.geyser:api:2.9.0-SNAPSHOT
  - paperweight.paperDevBundle("1.21.10-R0.1-SNAPSHOT")

- Target Java version in build.gradle:
  - targetJavaVersion = 21 (c:\Users\PC_User\IdeaProjects\bettersurvival\build.gradle:41-49)
  - Ensure dev machine JDK >= 21 or Gradle toolchain uses JDK 21.

## High-level architecture
- Plugin entry point: Loader.java (c:\Users\PC_User\IdeaProjects\bettersurvival\src\main\java\org\pexserver\koukunn\bettersurvival\Loader.java)
  - Manages initialization and registration of modules, commands, event listeners.
  - See feature registration lines: c:\Users\PC_User\IdeaProjects\bettersurvival\src\main\java\org\pexserver\koukunn\bettersurvival\Loader.java:33-73
  - Toggle features are registered in Loader.registerCommands() and at lines 76-95.

- Core modules:
  - Core.Command: command framework
    - CommandManager.java: c:\Users\PC_User\IdeaProjects\bettersurvival\src\main\java\org\pexserver\koukunn\bettersurvival\Core\Command\CommandManager.java
    - CommandGuide.java: usage & how to add commands (c:\Users\PC_User\IdeaProjects\bettersurvival\src\main\java\org\pexserver\koukunn\bettersurvival\Core\Command\CommandGuide.java)

  - Core.Config:
    - ConfigManager.java (c:\Users\PC_User\IdeaProjects\bettersurvival\src\main\java\org\pexserver\koukunn\bettersurvival\Core\Config\ConfigManager.java)
    - PEXConfig.java: config structure and file storage (c:\Users\PC_User\IdeaProjects\bettersurvival\src\main\java\org\pexserver\koukunn\bettersurvival\Core\Config\PEXConfig.java)
    - JsonUtils.java: serialization helper (c:\Users\PC_User\IdeaProjects\bettersurvival\src\main\java\org\pexserver\koukunn\bettersurvival\Core\Config\JsonUtils.java)

  - Modules:
    - ToggleModule (UI toggles + feature metadata): c:\Users\PC_User\IdeaProjects\bettersurvival\src\main\java\org\pexserver\koukunn\bettersurvival\Modules\ToggleModule.java
    - Feature modules (in src/main/java/org/.../Modules/Feature/*)
      - ChestLock (protection), ChestSort, ChestShop, TreeMine, OreMine, AutoFeed, AnythingFeed, AutoPlant, BedrockSkin, TPA, etc.

- Data storage & config:
  - JSON files under plugin data with PEXConfig format. Refer to README and ConfigManager.
  - Example stored files: plugins/Bettersurvival/config.yml (README mentions it), and `PEXConfig` dir (persisted via ConfigManager).

- Patterns and conventions:
  - Modules are event listeners registered in Loader#onEnable.
  - ToggleModule drives UI/toggles and default feature visibility. See ToggleModule usage and methods (c:\Users\PC_User\IdeaProjects\bettersurvival\src\main\java\org\pexserver\koukunn\bettersurvival\Modules\ToggleModule.java).
  - Commands extend BaseCommand and are registered via CommandManager.register in Loader.registerCommands() (c:\Users\PC_User\IdeaProjects\bettersurvival\src\main\java\org\pexserver\koukunn\bettersurvival\Core\Command\CommandGuide.java:7-13, Loader.java:130-145).

## Important files & locations
- build.gradle — c:\Users\PC_User\IdeaProjects\bettersurvival\build.gradle
  - Plugins and runServer task: c:\Users\PC_User\IdeaProjects\bettersurvival\build.gradle:1-38
- settings.gradle — c:\Users\PC_User\IdeaProjects\bettersurvival\settings.gradle
- gradle.properties — c:\Users\PC_User\IdeaProjects\bettersurvival\gradle.properties
- README.md — c:\Users\PC_User\IdeaProjects\bettersurvival\README.md
- Copilot instructions — c:\Users\PC_User\IdeaProjects\bettersurvival\.github\copilot-instructions.md (very useful for patterns & onboarding)
- GitHub release workflow — c:\Users\PC_User\IdeaProjects\bettersurvival\.github\workflows\release.yml
- Loader.java — plugin entry: c:\Users\PC_User\IdeaProjects\bettersurvival\src\main\java\org\pexserver\koukunn\bettersurvival\Loader.java
- Core & Modules sources: c:\Users\PC_User\IdeaProjects\bettersurvival\src\main\java\org\pexserver\koukunn\bettersurvival\...
- Dev server run directory: c:\Users\PC_User\IdeaProjects\bettersurvival\run\ (contains run/logs, plugins, server files)

## GitHub / CI
- Release workflow builds the JAR and uploads release via GitHub Actions: c:\Users\PC_User\IdeaProjects\bettersurvival\.github\workflows\release.yml

## Copilot & AI Dev rules (key excerpts)
From c:\Users\PC_User\IdeaProjects\bettersurvival\.github\copilot-instructions.md:
- Project type: Minecraft Paper plugin; plugin entry is Loader.java
- Commands: Add commands by extending BaseCommand and registering in Loader#registerCommands
- Config patterns: PEXConfig JSON storage; ConfigManager loads/saves JSON; see chest/ChestLock store code for examples
- Build & dev flows:
  - Build JAR: `./gradlew.bat build`
  - Run dev server: `./gradlew.bat runServer`
  - Manual plugin testing: Copy to `run/plugins/` and restart
- Where to start: Loader.java; Core.Command.*; Core.Config.*; Modules/*

Important: Use the Copilot file above as the on-boarding checklist for any code generation or changes.

## Typical developer workflows
Follow these steps for common tasks:

- Local build
  - Build:
    ```bash
    ./gradlew build
    ```
  - Run the dev server:
    ```bash
    ./gradlew runServer
    ```

- Manual testing
  1. Build jar: `./gradlew build`
  2. Copy build/libs/Bettersurvival-<version>.jar to run/plugins/
  3. Restart server or `./gradlew runServer` to boot dev server with the plugin loaded.

- Add a feature module
  1. Implement a new module as a listener (create a class in Modules/Feature/<Name>).
  2. Register in Loader#onEnable, and add ToggleModule.registerFeature if required.
  3. Add PEXConfig read/write patterns if persistent storage required (see ChestShopStore.java: path read/writes).

- Add a command
  1. Extend BaseCommand and implement required methods.
  2. Register with CommandManager in Loader.registerCommands().

- Debugging
  - Attach a debugger to the JVM launched by `runServer`. There are two options:
    1. Enable remote debugging by editing build.gradle runServer configuration:
       - Example (not present by default; for dev only):
       ```groovy
       tasks.runServer {
           jvmArgs = ['-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005']
       }
       ```
       Then run `./gradlew runServer` and attach IDE debugger to port 5005.
    2. If you want to debug a Gradle-run process, add `--no-daemon` and necessary JVM args via Gradle properties or environment variables.
  - Check server logs:
    - Current logs: c:\Users\PC_User\IdeaProjects\bettersurvival\run\logs\latest.log.gz or extracted log files.

## Tests
- This project currently has no tests in src/test (c:\Users\PC_User\IdeaProjects\bettersurvival\src\test not present)
- Add tests by including test dependencies and source sets:
  - Example for JUnit 5:
    ```groovy
    dependencies {
      testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
    }
    test {
      useJUnitPlatform()
    }
    ```
  - Run a single test:
    ```bash
    ./gradlew test --tests "org.pexserver.koukunn.bettersurvival.YOUR_TEST_CLASS.yourMethod"
    ```

## Lint & Static Analysis
- No static analysis / lint tools are currently configured.
- If needed, add Spotless / Checkstyle / PMD in build.gradle.

## Releasing
- Releases are automated by the GitHub workflow:
  - c:\Users\PC_User\IdeaProjects\bettersurvival\.github\workflows\release.yml
  - The workflow builds (`./gradlew build`) and creates releases/upload assets automatically.

## Important notes / inconsistencies & recommended checks
- build.gradle sets targetJavaVersion = 21 (c:\Users\PC_User\IdeaProjects\bettersurvival\build.gradle:41-49) but README shows Java 17+ badge (c:\Users\PC_User\IdeaProjects\bettersurvival\README.md:4).
  - Choose a single value for the repo (matching CI & dev environments).
- plugin.yml is referenced in README and build.gradle `processResources` but does not appear under src/main/resources in the repository (no plugin.yml found).
  - Confirm plugin.yml is present or add it under: c:\Users\PC_User\IdeaProjects\bettersurvival\src\main\resources\plugin.yml
- There are no test sources; add tests to increase reliability.

## File references (key lines)
- Project root: c:\Users\PC_User\IdeaProjects\bettersurvival
- build.gradle (plugins, runServer, targetJavaVersion): c:\Users\PC_User\IdeaProjects\bettersurvival\build.gradle:1-49 and 32-38
- Loader (entry & feature registration): c:\Users\PC_User\IdeaProjects\bettersurvival\src\main\java\org\pexserver\koukunn\bettersurvival\Loader.java:33-73 and 76-95; command registration: Loader.java:130-145
- Command guide: c:\Users\PC_User\IdeaProjects\bettersurvival\src\main\java\org\pexserver\koukunn\bettersurvival\Core\Command\CommandGuide.java
- Config manager: c:\Users\PC_User\IdeaProjects\bettersurvival\src\main\java\org\pexserver\koukunn\bettersurvival\Core\Config\ConfigManager.java
- ToggleModule: c:\Users\PC_User\IdeaProjects\bettersurvival\src\main\java\org\pexserver\koukunn\bettersurvival\Modules\ToggleModule.java
- README onboarding & commands: c:\Users\PC_User\IdeaProjects\bettersurvival\README.md
- Copilot instructions: c:\Users\PC_User\IdeaProjects\bettersurvival\.github\copilot-instructions.md
- CI workflow for releases: c:\Users\PC_User\IdeaProjects\bettersurvival\.github\workflows\release.yml

## Troubleshooting tips
- If runServer fails due to Java version mismatch, install the Java version specified or update `targetJavaVersion` in build.gradle.
- If plugin is not loaded by the server, check plugin.yml exists under src/main/resources and contains correct `main` class path and plugin name.
- Check `run/logs/latest.log` for server plugin load errors and stacktraces.
- To validate the plugin jar contents, run:
  ```bash
  jar tf build/libs/Bettersurvival-<version>.jar
  ```

---

If you want, I can:
- Create a minimal test skeleton (JUnit 5) and Gradle test config to enable CI test runs.
- Draft small ADVICE or GH Issue for plugin.yml/Java version mismatch if you want to reconcile differences.
Which next step would you like?
