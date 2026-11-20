# 存档与数据库设置

Utopia Server Panel 采用单存档独立 SQLite 数据库的隔离架构，确保同一服务端切换不同世界存档时，用户账号、权限、监控历史与地图缓存互不污染。

## 按存档隔离的数据目录

服务端启动并加载世界后，面板会在当前激活的世界存档目录下自动创建 `utopia_server_panel` 子目录，用于存放该存档专属的全部面板数据：

- `utopia_server_panel/utopia_server_panel.db`：该存档独立的 SQLite 数据库，包含用户、角色、权限、配置、绑定关系、监控日志、路径点、白名单申请与聊天记录。
- `utopia_server_panel/map_cache/`：网页小地图渲染生成的磁盘瓦片缓存目录。

同时，面板会在 `config/utopia_server_panel_worlds.json` 中维护一份轻量级存档索引，记录各个历史存档的路径、世界名称、Minecraft 版本与最近活跃时间，便于在「存档与数据库」页面集中查看各存档的数据库大小与记录统计。

## 旧版数据库自动迁移

如果服务端此前在 `config/utopia_server_panel.db` 中存有旧版单库数据，且当前世界存档目录下尚未生成独立数据库，面板在启动时会自动完成以下迁移步骤：

1. 将 `config/utopia_server_panel.db` 及其 WAL/SHM 附属文件复制到当前世界存档的 `utopia_server_panel/utopia_server_panel.db`。
2. 将原 `config/utopia_server_panel.db` 重命名为 `config/utopia_server_panel.db.migrated` 作为备份保留。
3. 清理残留的临时 WAL/SHM 文件并正常初始化新路径数据库。

## 数据库维护建议

- 备份服务器世界存档文件夹时，面板数据库与地图缓存会随存档目录一并备份，无需额外配置外部数据库备份任务。
- 当历史监控日志或聊天记录占用空间过大时，可通过调整 [面板设置](/settings/panel) 中的 `monitorRetentionDays` 与 [游戏内联动设置](/settings/ingame) 中的 `chatRetentionDays` 控制自动清理周期。
- 当地图瓦片缓存超出需求时，可在小地图管理设置中调整 `mapCacheMaxMb` 上限或执行清理地图缓存操作。
