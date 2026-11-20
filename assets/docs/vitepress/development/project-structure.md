# 项目结构

Utopia Server Panel 采用跨版本、跨加载器的模块化分层架构，将平台无关的核心面板服务与各个 Minecraft 版本的游戏适配器解耦，使同一套网页前端与后端逻辑可以覆盖 `1.7.10` 至 `26.3` 的 Forge、NeoForge 与 Fabric。

## 顶层目录概览

| 目录 | 职责说明 |
| --- | --- |
| `core/` | 平台无关的核心服务层，包含内嵌 Netty HTTP/WebSocket 服务器、SQLite 与 MyBatis 数据层、鉴权体系、小地图渲染引擎、性能分析器、系统监控与 `GameAdapter` 抽象接口 |
| `bridge/modern-jvm/` | 面向现代 Java 版本的桥接模块 |
| `platforms/modern-shared/` | 面向 `1.16.5` 及以上现代版本的共享适配层源码与资源，配合预处理器按目标版本与加载器裁剪生成代码 |
| `platforms/forge/` | 现代 Forge 工程入口，支持 `1.16.5` 至 `26.3` |
| `platforms/neoforge/` | NeoForge 工程入口，支持 `1.20.4` 至 `26.3` |
| `platforms/fabric/` | Fabric 工程入口，支持 `1.16.5` 至 `26.3` |
| `legacy/forge-1.12.2/` | Forge `1.12.2` 独立适配工程 |
| `legacy/forge-1.7.10/` | Forge `1.7.10` 独立适配工程 |
| `web/` | 基于 Vue 3、TypeScript、Naive UI、Pinia 与 ECharts 构建的网页前端单页应用 |
| `gradle/` | 共享构建脚本，包含 `preprocess.gradle` 源码预处理、`packaging.gradle` Shadow 打包重定位与 `constants.gradle` |
| `scripts/` | CI 辅助构建与版本矩阵编排脚本 `ci_helpers.py` |
| `versions.json` | 全版本矩阵清单，定义各 Minecraft 版本的 JDK、加载器、插件类型与 Netty/SLF4J 打包策略 |

## core 核心模块划分

`core/src/main/java/com/utopiaxc/utopiaserverpanel/` 下的主要子包职责如下：

- `adapter/`：`GameAdapter` 接口与 `AdapterRegistry`，定义获取在线玩家、执行指令、读取世界信息、控制白名单等跨版本统一契约。
- `auth/`：密码哈希、JWT 签发与校验、TOTP 算法、WebAuthn CBOR 解析与工作量证明验证码。
- `command/`：`/usp` 游戏内指令的统一解析与输出服务。
- `config/`：`PanelConfig` 读写 `config/utopia_server_panel.toml`。
- `spi/`：扩展服务接口定义，包含小地图瓦片提供者 `MapTileProvider`、性能热点快照以及未来联动其他模组的 `CraftingNetworkProvider`。
- `system/`：基于 JDK 的 CPU、内存与磁盘监控采集。
- `web/`：Netty 服务器入口、中间件管道、路由分发、控制器、MyBatis Mapper、SQLite Schema 以及业务 Service 层。

## 前端与构建产物融合

`web/` 目录编译后的静态产物会输出至面板资源目录并打入最终模组 `.jar` 中。服务端运行时，Netty `StaticFileHandler` 直接从模组资源中提供单页应用静态文件，实现单文件开箱即用。
