# Utopia Server Panel

> A lightweight, multi-version web management panel mod for Minecraft servers.

**Language**: English | [简体中文](assets/docs/README.md)

Utopia Server Panel embeds a self-contained Web UI, real-time console, server monitor, and player-account binding service directly inside Minecraft, supporting Forge, NeoForge, and Fabric from 1.7.10 to 26.3.

Notice: Part of this project is built with generative AI assistance.

## Download

[Latest Releases](https://github.com/UtopiaXC/UtopiaServerPanel/releases)

Choose the `.jar` matching your Minecraft version and mod loader from the release build matrix. Every build is a single standalone mod file with no external database or runtime dependencies required.

## Supported Platforms

| Platform | Minecraft Versions | Java |
| --- | --- | --- |
| Forge | `1.7.10`, `1.12.2`, `1.16.5` – `26.3` | Java 8+ (1.7.10–1.16.5), Java 17+ (1.18–1.20.4), Java 21+ (1.20.6–1.21.11), Java 25+ (26.x) |
| NeoForge | `1.20.4` – `26.3` | Java 17+ (1.20.4), Java 21+ (1.20.6–1.21.11), Java 25+ (26.x) |
| Fabric | `1.16.5` – `26.3` | Java 8+ (1.16.5), Java 17+ (1.18–1.20.4), Java 21+ (1.20.6–1.21.11), Java 25+ (26.x) |

## Features

- **Server Overview & Monitoring**: Live TPS, MSPT, memory, CPU, disk, and online player status with historical metrics.
- **Real-Time Web Console**: Live server log streaming, command execution, and Tab completion over WebSocket.
- **Role-Based Access Control**: Granular module permissions (`none`, `read`, `full`), custom roles, and API tokens.
- **Account Security & Recovery**: JWT sessions, PoW captcha, TOTP two-factor authentication, SMTP verification, and `/usp` in-game account binding and recovery.
- **Responsive Theme UI**: Light and dark mode with bilingual English / Chinese interface.

## Quick Start

1. Place the downloaded `.jar` into your server's `mods/` directory and start the server.
2. Check `config/utopia_server_panel.toml` (default port: `55533`) or retrieve the initial setup token in-game with `/usp setup token`.
3. Open `http://<server-ip>:55533` in your browser to finish the administrator setup wizard.

## Commands

- `/usp setup token` — Display the one-time setup token during initial configuration.
- `/usp bind` — Generate a code to bind your Minecraft player to a panel account.
- `/usp unbind` — Unbind your Minecraft player from the panel account.
- `/usp recover password` — Generate a password recovery token in-game.
- `/usp recover 2fa` — Generate a 2FA reset token in-game.

## Contributing

Contributions are welcome.  
AI-assisted pull requests are accepted, provided all changes are reviewed and tested by hand before submission.  
Please follow standard GitHub guidelines when opening issues or pull requests.

## License

All Rights Reserved. See [LICENSE.txt](LICENSE.txt) for details.

## UI/UX Demo

| Dashboard | Console |
| --- | --- |
| ![dashboard](assets/images/dashboard.png) | ![console](assets/images/console.png) |
