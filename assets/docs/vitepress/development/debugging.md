# 调试方法

在本地开发过程中，可以根据需要单独调试前端界面，或结合 Gradle 启动对应 Minecraft 版本与加载器的开发环境服务端进行联调。

## 准备环境

- **Node.js**：用于构建和热更新调试 `web/` 前端项目。
- **JDK**：依据 `versions.json` 中目标 Minecraft 版本的要求准备对应版本的 JDK，例如 `1.7.10` 与 `1.12.2` 运行需要 JDK 8，`1.21.1` 需要 JDK 21，`26.3` 需要 JDK 25。

## 前端独立开发与构建

进入 `web/` 目录安装依赖后，可以启动 Vite 开发服务器或编译生产包：

```bash
cd web
npm install
npm run dev
```

当需要将最新前端代码打包嵌入模组进行完整测试时，执行构建命令：

```bash
cd web
npm run build
```

构建完成后执行对应平台工程的 Gradle 构建或运行任务，即可在面板中看到最新的前端页面。

## 启动指定版本的服务端调试

项目的各加载器工程位于 `platforms/` 与 `legacy/` 目录下，均通过 `-Ptarget_mc` 指定目标 Minecraft 版本，运行目录统一指向根目录的 `run/`。

以调试 **NeoForge 1.21.1** 服务端为例：

```bash
cd platforms/neoforge
../../gradlew runServer -Ptarget_mc=1.21.1
```

以调试 **Fabric 1.21.1** 服务端为例：

```bash
cd platforms/fabric
../../gradlew runServer -Ptarget_mc=1.21.1
```

以调试 **Forge 1.21.1** 或 **1.16.5** 为例：

```bash
cd platforms/forge
../../gradlew runServer -Ptarget_mc=1.21.1
```

以调试 **Forge 1.12.2** 或 **1.7.10** 为例：

```bash
cd legacy/forge-1.12.2
../../gradlew runServer
```

服务端启动后，观察控制台输出的初始化端口，默认为 `55533`，以及 `config/utopia_server_panel.toml` 中的 `setupToken`，即可在浏览器打开 `http://127.0.0.1:55533` 进行联调。

## 运行核心单元测试

`core/` 模块包含鉴权、服务逻辑与玩家主页相关的单元测试，可在仓库根目录直接运行：

```bash
./gradlew :core:test
```
