# Project Structure

Utopia Server Panel uses a modular, cross-version architecture that decouples platform-independent panel services from version-specific Minecraft adapters, allowing a single frontend and backend codebase to support Forge, NeoForge, and Fabric from `1.7.10` to `26.3`.

## Top-Level Directories

| Directory | Responsibility |
| --- | --- |
| `core/` | Platform-independent core services including the embedded Netty HTTP/WebSocket server, SQLite and MyBatis persistence, authentication, minimap renderer, profiler, system monitor, and `GameAdapter` interfaces |
| `bridge/modern-jvm/` | Bridge module targeting modern Java runtimes |
| `platforms/modern-shared/` | Shared adapter source and resources for `1.16.5` and newer, processed per target Minecraft version and loader |
| `platforms/forge/` | Modern Forge entry point supporting `1.16.5` through `26.3` |
| `platforms/neoforge/` | NeoForge entry point supporting `1.20.4` through `26.3` |
| `platforms/fabric/` | Fabric entry point supporting `1.16.5` through `26.3` |
| `legacy/forge-1.12.2/` | Dedicated Forge `1.12.2` adapter project |
| `legacy/forge-1.7.10/` | Dedicated Forge `1.7.10` adapter project |
| `web/` | Single-page web frontend built with Vue 3, TypeScript, Naive UI, Pinia, and ECharts |
| `gradle/` | Shared Gradle scripts including `preprocess.gradle`, `packaging.gradle`, and `constants.gradle` |
| `scripts/` | CI matrix orchestration and build helper script `ci_helpers.py` |
| `versions.json` | Version matrix manifest defining JDK versions, loaders, plugin types, and Netty/SLF4J packaging rules |

## Core Package Layout

Inside `core/src/main/java/com/utopiaxc/utopiaserverpanel/`:

- `adapter/`: `GameAdapter` contract and `AdapterRegistry` abstracting online players, commands, world info, and whitelist control across Minecraft versions.
- `auth/`: Password hashing, JWT issuance and validation, TOTP, WebAuthn CBOR parsing, and Proof-of-Work captcha.
- `command/`: Unified `/usp` in-game command parser and output service.
- `config/`: `PanelConfig` managing `config/utopia_server_panel.toml`.
- `spi/`: Extension service provider interfaces including `MapTileProvider`, profiler hotspot snapshots, and `CraftingNetworkProvider` for future mod integrations.
- `system/`: JDK-based CPU, memory, and disk telemetry collection.
- `web/`: Netty server bootstrap, middleware pipeline, router, controllers, MyBatis mappers, SQLite schema, and business services.

## Frontend Asset Bundling

Compiled static assets from `web/` are bundled directly into the mod `.jar`. At runtime, Netty `StaticFileHandler` serves the SPA directly from mod resources for single-file deployment.
