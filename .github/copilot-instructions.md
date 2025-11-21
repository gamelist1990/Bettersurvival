# Bettersurvival — Copilot Instructions

This file contains actionable notes for AI coding agents to be immediately productive in the Bettersurvival codebase.

Overview
- **Project type:** Minecraft Paper (1.21.10) plugin written in Java.
- **Plugin entry:** `Loader` in `src/main/java/org/pexserver/koukunn/bettersurvival/Loader.java`.
- **Command registration:** `Core.Command.CommandManager` dynamically registers commands.
- **Config storage:** JSON files under `<plugin_data>/PEXConfig` via `Core.Config.ConfigManager` + `PEXConfig`.

Key architecture & patterns
- **Modules + Listener model:** Features are implemented as modules (e.g., `Modules/Feature/*`) and registered as listeners in `Loader::onEnable`.
  - Example: `TreeMineModule` and `OreMineModule` are registered with `getServer().getPluginManager().registerEvents(module, this);`.
- **Toggle features:** `ToggleModule` registers features and stores toggles via ConfigManager. UI interactions handled by `ToggleListener`.
  - Use `ToggleModule.registerFeature(new ToggleModule.ToggleFeature(key, name, description, Material.X));` in `Loader` to add new toggle features.
- **Command system:** Extend `Core.Command.BaseCommand` and register via `CommandManager.register(...)` in `Loader#registerCommands()`.
  - Permissions use `Core.Command.PermissionLevel` or override `getPermission()` to use custom permission strings.
- **Data storage:** Persist small domain objects (e.g., `ChestLock`) with `ChestLockStore` into `PEXConfig` JSON files. Look at `Core.Config.JsonUtils` (if present) for serialization rules.

Build & developer flows
- **Build JAR:** `./gradlew.bat build` (Windows). Output: `build/libs/Bettersurvival-<version>.jar`.
- **Run dev server:** `./gradlew.bat runServer` (Uses `xyz.jpenilla.run-paper` plugin configured in `build.gradle` — see `runServer.minecraftVersion()` setting).
  - This task automatically uses the built plugin JAR; server files live in `/run`.
- **Manual plugin testing:** Copy the built JAR to `run/plugins/Bettersurvival/` and restart the server if needed.

Conventions & repo-specific patterns
- **Config paths:** Paths passed to `ConfigManager.loadConfig()` are relative to `<plugin_data>/PEXConfig` (e.g., `toggles/users/<uuid>.json`, `features/descriptions.json`, `ChestLock/chestlocks.json`).
- **Feature visibility rules:** `ToggleModule.getVisibleFeatures(adminMode)` filters features by `hasGlobal` and `isUserToggleAllowed()`. When adding a new feature, consider admin vs user toggling semantics.
- **Command flow & error handling:** `CommandManager` wraps commands with `CommandWrapper` that handles permission checks and centralized try/catch which logs and sends a friendly message on failure.

Integration points
- **Paper API:** `compileOnly` dependency in `build.gradle`. Running locally requires `runServer` or placing jar in `run/plugins`.
- **Floodgate / Cumulus:** Optional `compileOnly` dependencies — treat as platform integrations (presence is compile-time only).

Tips for contributors / AI agents
- When adding a feature module:
  - Implement `Listener` and add event handlers.
  - Register module in `Loader#onEnable`, add toggle via `ToggleModule.registerFeature()` when relevant.
  - Persist feature metadata in `PEXConfig` using `ConfigManager`.
- When adding a command:
  - Extend `BaseCommand`, implement `getName`, `getDescription`, `execute`, override `getPermissionLevel` or `getPermission` if needed.
  - Register command in `Loader#registerCommands()`.
- When working on JSON config format: use `PEXConfig`'s `Map<String, Object>` and keep values stable; `ChestLockStore` shows how to read/write nested structures.

Where to start reading code
- `Loader.java` — plugin lifecycle and feature wiring.
- `Core.Command.*` — how commands, permissions and registration works.
- `Core.Config.*` — JSON config handling conventions.
- `Modules/*` — event-driven feature implementations.

If unclear / missing
- Ask for examples of expected behaviors (e.g., how toggles should behave when global is false). Offer to add small unit or integration tests for config and command parsing.

---
Be brief and reference source before making wide changes (e.g., do not bulk-modify `Loader` without confirming backwards compatibility with existing features).