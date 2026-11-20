# Debugging Guide

During local development, you can iterate on the web frontend independently or launch a Minecraft development server for any supported version and loader via Gradle.

## Prerequisites

- **Node.js**: Required to run the Vite dev server and build the `web/` frontend.
- **JDK**: Install the JDK version required by your target Minecraft version in `versions.json`, such as JDK 8 for `1.7.10` and `1.12.2`, JDK 21 for `1.21.1`, and JDK 25 for `26.3`.

## Frontend Development & Build

Install dependencies inside `web/` and start the Vite dev server:

```bash
cd web
npm install
npm run dev
```

To bundle the latest frontend assets into the mod `.jar` for full integration testing, run:

```bash
cd web
npm run build
```

Then build or run the target platform module with Gradle to load the updated frontend.

## Running a Development Server

Loader projects under `platforms/` and `legacy/` accept `-Ptarget_mc` to select the target Minecraft version, sharing the root `run/` directory.

Run **NeoForge 1.21.1** server:

```bash
cd platforms/neoforge
../../gradlew runServer -Ptarget_mc=1.21.1
```

Run **Fabric 1.21.1** server:

```bash
cd platforms/fabric
../../gradlew runServer -Ptarget_mc=1.21.1
```

Run **Forge 1.21.1** or **1.16.5** server:

```bash
cd platforms/forge
../../gradlew runServer -Ptarget_mc=1.21.1
```

Run **Forge 1.12.2** or **1.7.10** server:

```bash
cd legacy/forge-1.12.2
../../gradlew runServer
```

Once started, check the console log for the web port, defaulting to `55533`, and `setupToken` in `config/utopia_server_panel.toml`, then open `http://127.0.0.1:55533` in your browser.

## Running Core Unit Tests

The `core/` module includes unit tests covering authentication, services, and player profiles. Run them from the repository root:

```bash
./gradlew :core:test
```
