# Utopia Server Panel

> 适用于我的世界多版本、多加载器的轻量级 Web 服务器管理面板模组。

**语言**：[English](../../../README.md) | 简体中文

Utopia Server Panel 将 Web 管理后台、实时控制台、状态监控以及玩家账号绑定系统直接内置于 Minecraft 服务端中，支持从 `1.7.10` 到 `26.3` 的 Forge、NeoForge 与 Fabric。


## 在线演示
[在我的服务器上运行的一个All The Mod 10客户端](https://mc-panel.utopiaxc.com/)

## Download
在对应版本的 Build Matrix 表格中直接点击适合你游戏版本与加载器的 `.jar` 即可下载。单文件开箱即用，无需额外安装数据库或外部运行库。

### CurseForge
推荐。如果显示404或只有更旧的版本，可能最新版本正在审查，如有需要请从Github Release下载。  
[CurseForge: Utopia Server Panel](https://www.curseforge.com/minecraft/mc-mods/utopia-server-panel)  

### Github Release
[Latest Releases](https://github.com/UtopiaXC/UtopiaServerPanel/releases)  


## 兼容矩阵
**注意：** 只有1.7.10，1.12.2，1.16.5，1.21.1和26.3进行了本地开发环境冒烟测试，如果你希望在其他版本中安装本mod，请谨慎使用。  

| 加载器 | 支持的游戏版本 | 运行环境 |
| --- | --- | --- |
| Forge | `1.7.10`、`1.12.2`、`1.16.5` – `26.3` | Java 8+（1.7.10–1.16.5）、Java 17+（1.18–1.20.4）、Java 21+（1.20.6–1.21.11）、Java 25+（26.x） |
| NeoForge | `1.20.4` – `26.3` | Java 17+（1.20.4）、Java 21+（1.20.6–1.21.11）、Java 25+（26.x） |
| Fabric | `1.16.5` – `26.3` | Java 8+（1.16.5）、Java 17+（1.18–1.20.4）、Java 21+（1.20.6–1.21.11）、Java 25+（26.x） |

## 核心功能

- **多用户、角色管理与玩家绑定**：支持多用户账号、基于角色的细粒度权限控制与多游戏角色绑定。
- **Web 命令行**：通过浏览器实时执行服务端指令并支持自动补全。
- **服务器运行日志与实时监控**：实时查看运行日志、TPS、MSPT、硬件资源占用、在线玩家与历史监控数据。
- **Web 小地图**：在浏览器中浏览实时同步的多维度地形地图、在线玩家与路径点。
- **白名单管理**：支持网页端在线申请、审核通知与服务端白名单管理。
- **站内信与游戏内聊天联动**：支持网页聊天室与游戏内聊天双向同步以及站内信通知。
- **模组与服务器配置编辑**：支持在线查看已安装模组并编辑 `server.properties` 与模组配置文件。

## 快速开始

1. 将下载的 `.jar` 放入服务端 `mods/` 目录并启动服务器。
2. 查看 `config/utopia_server_panel.toml`（默认端口 `55533`），或在游戏内/后台查看初始化令牌 `/usp setup token`。
3. 在浏览器访问 `http://<服务器IP>:55533`，按引导完成管理员初始化配置。

## 游戏内指令

- `/usp setup token` — 获取初始化向导所需的一次性令牌。
- `/usp bind` — 生成用于绑定 Minecraft 角色与面板账号的绑定码。
- `/usp unbind` — 解除当前玩家与面板账号的绑定关系。
- `/usp recover password` — 在游戏内生成面板密码重置令牌。
- `/usp recover 2fa` — 在游戏内生成两步验证重置令牌。

## 社区协作

本项目欢迎贡献代码。  
欢迎提交 AI 辅助编写的代码，但在提交 PR 前请务必经过人工审阅与测试。  
请遵守社区规范提交 Issue 与 Pull Request。


## UI/UX Demo


| 初始化界面 | 面板首页 |
| --- | --- |
| ![initialization](../../images/init.png) | ![dashboard](../../images/dashboard.png) |  


| 命令行 | 性能监控记录 |
| --- | --- |
| ![console](../../images/console.png) | ![monitor](../../images/monitor.png) |
