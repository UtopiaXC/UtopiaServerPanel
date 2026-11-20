# API 接口文档

Utopia Server Panel 提供了一套完整的 HTTP REST API 与 WebSocket 实时通道，支持使用个人 API 访问令牌进行自动化脚本调用、外部监控集成或第三方工具对接。

## 认证方式与令牌格式

用户登录后可在 **个人设置 -> API 访问令牌** 页面创建个人访问令牌。令牌以 `usp_` 为前缀，仅在创建时完整展示一次，服务端数据库仅保存其 SHA-256 哈希值。

发起 HTTP 请求时，在请求头中携带 Bearer 令牌：

```http
Authorization: Bearer usp_your_token_here
```

连接 WebSocket `/ws` 时，可在连接后发送认证消息帧：

```json
{
  "action": "auth",
  "token": "usp_your_token_here"
}
```

## 作用域与权限规则

个人访问令牌的最终生效权限由**所属账号的角色权限**与**令牌自身的作用域 `scopes`** 共同决定，两者取交集：

- 当令牌作用域为 `*` 时，拥有所属账号的全部权限。
- 当令牌指定了受限作用域，例如 `dashboard:1,terminal:2` 时，仅允许访问匹配模块且不超过令牌等级的接口。
- 涉及账号敏感凭证与绑定变更的写操作，包括 `/api/me/*` 与 `/api/binding/*` 下的非 `GET` 请求，仅允许作用域为 `*` 的令牌执行，受限作用域令牌会被拦截并返回 `INSUFFICIENT_SCOPE`。

## HTTP 接口参考

以下按功能模块列出可通过 API 访问令牌调用的主要接口。

### 个人资料与自身状态

| 方法 | 路径 | 权限或作用域要求 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/me` | 已认证用户，受限令牌需 `profile:1` 或 `read` | 获取当前账号信息与权限矩阵 |
| `GET` | `/api/me/sessions` | 同上 | 获取当前活跃会话列表 |
| `GET` | `/api/me/login-history` | 同上 | 获取最近 20 条登录审计记录 |
| `GET` | `/api/me/tokens` | 同上 | 列出当前账号创建的 API 令牌元数据 |
| `POST` | `/api/me/tokens` | 仅 `*` 作用域 | 创建新的个人访问令牌 |
| `DELETE` | `/api/me/tokens/{id}` | 仅 `*` 作用域 | 吊销指定 API 令牌 |
| `GET` | `/api/binding/list` | 已认证用户，受限令牌需 `profile:1` 或 `read` | 获取当前账号绑定的 Minecraft 角色列表 |
| `GET` | `/api/player/me` | 同上 | 获取主绑定角色的游戏内实时数据 |

### 服务器状态与监控日志

| 方法 | 路径 | 权限或作用域要求 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/status` | `dashboard:1` | 获取服务器实时状态快照、TPS、MSPT、在线人数与硬件资源 |
| `GET` | `/api/monitor/config` | 公开或已认证 | 获取监控展示配置 |
| `GET` | `/api/monitor/perf` | `logs:1` | 查询历史性能采样记录 |
| `GET` | `/api/monitor/players` | `logs:1` | 查询玩家上下线事件记录 |
| `GET` | `/api/monitor/lifecycle` | `logs:1` | 分页查询服务器启停生命周期记录 |
| `GET` | `/api/monitor/lifecycle/range` | `logs:1` | 按时间区间查询服务器启停记录 |

### 性能分析与网页小地图

| 方法 | 路径 | 权限或作用域要求 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/profiler/snapshot` | `profiler:1` | 获取当前维度耗时与区块热点快照 |
| `POST` | `/api/profiler/teleport` | `profiler:2` | 将绑定角色传送至热点坐标 |
| `GET` | `/api/map/bootstrap` | `map:1` | 获取小地图初始化配置、维度列表与权限信息 |
| `GET` | `/api/map/tile` | `map:1` | 获取指定维度与缩放层级的地图瓦片图像 |
| `GET` | `/api/map/regions` | `map:1` | 查询已渲染或可用区域坐标 |
| `GET` | `/api/map/overlays` | `map:1` | 获取实时玩家、实体、生物与路径点叠加数据 |
| `POST` | `/api/map/waypoints` | `map:2` | 创建地图路径点 |
| `DELETE` | `/api/map/waypoints/{id}` | `map:2` | 删除指定路径点 |
| `POST` | `/api/map/teleport` | `map:1` 与地图传送配置 | 传送至地图指定坐标 |
| `POST` | `/api/map/reset-health` | `admin:2` | 重置地图渲染健康度状态 |
| `POST` | `/api/map/clear-cache` | `admin:2` | 清理小地图磁盘瓦片缓存 |

### 模组与服务器配置

| 方法 | 路径 | 权限或作用域要求 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/mods/overview` | `mods_config:1` | 获取已安装模组列表与配置文件树 |
| `GET` | `/api/mods/config` | `mods_config:1` | 读取指定模组配置文件内容 |
| `PUT` | `/api/mods/config` | `mods_config:2` | 保存修改后的模组配置文件 |
| `GET` | `/api/mods/server-properties` | `mods_config:1` | 读取 `server.properties` 内容 |
| `PUT` | `/api/mods/server-properties` | `mods_config:2` | 保存修改后的 `server.properties` |
| `POST` | `/api/mods/restart` | `mods_config:2` | 触发服务端重启命令 |

### 白名单与聊天消息

| 方法 | 路径 | 权限或作用域要求 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/whitelist/admin/applications` | `whitelist_manage:1` | 获取白名单申请列表 |
| `POST` | `/api/whitelist/admin/applications/{id}/review` | `whitelist_manage:2` | 审核通过或拒绝白名单申请 |
| `GET` | `/api/whitelist/admin/entries` | `whitelist_manage:1` | 获取服务端当前白名单成员 |
| `POST` | `/api/whitelist/admin/entries` | `whitelist_manage:2` | 手动添加白名单成员 |
| `DELETE` | `/api/whitelist/admin/entries/{name}` | `whitelist_manage:2` | 手动移除白名单成员 |
| `GET` | `/api/messages/inbox` | 已认证用户，受限令牌需 `messages:1` 或 `chat:1` | 获取站内信收件箱 |
| `GET` | `/api/messages/sent` | 同上 | 获取已发送站内信 |
| `GET` | `/api/messages/unread-count` | 同上 | 获取未读站内信数量 |
| `POST` | `/api/messages/send` | 已认证用户，受限令牌需 `messages:2` 或 `chat:2` | 发送站内信 |
| `POST` | `/api/messages/read-all` | 同上 | 全部标为已读 |
| `POST` | `/api/messages/{id}/read` | 同上 | 标记单条站内信已读 |
| `GET` | `/api/chat/history` | 已认证用户，受限令牌需 `chat:1` 或 `messages:1` | 获取游戏内聊天同步历史 |
| `POST` | `/api/chat/send` | 已认证用户，受限令牌需 `chat:2` 或 `messages:2` | 通过网页向游戏内发送聊天消息 |

### 系统管理

| 方法 | 路径 | 权限或作用域要求 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/admin/users` | `admin:1` | 获取用户列表 |
| `POST` | `/api/admin/users` | `admin:2` | 创建用户 |
| `PATCH` | `/api/admin/users/{id}` | `admin:2` | 更新用户信息或状态 |
| `DELETE` | `/api/admin/users/{id}` | `admin:2` | 删除用户 |
| `POST` | `/api/admin/users/{id}/unbind` | `admin:2` | 解除用户绑定的游戏角色 |
| `POST` | `/api/admin/users/{id}/reset-2fa` | `admin:2` | 重置用户两步验证 |
| `POST` | `/api/admin/users/{id}/unlock` | `admin:2` | 解锁被锁定的账号 |
| `GET` | `/api/admin/roles` | `admin:1` | 获取角色列表 |
| `GET` | `/api/admin/roles/{id}` | `admin:1` | 获取角色详情与权限矩阵 |
| `POST` | `/api/admin/roles` | `admin:2` | 创建自定义角色 |
| `PUT` | `/api/admin/roles/{id}` | `admin:2` | 更新角色权限矩阵 |
| `DELETE` | `/api/admin/roles/{id}` | `admin:2` | 删除自定义角色 |
| `GET` | `/api/admin/permissions` | `admin:1` | 获取系统全部权限键定义 |
| `GET` | `/api/admin/settings` | `admin:1` | 获取系统全局设置 |
| `PATCH` | `/api/admin/settings` | `admin:2` | 更新系统全局设置 |
| `GET` | `/api/admin/worlds` | `admin:1` | 获取已索引的世界存档与数据库统计 |

## WebSocket 接口参考

连接到 `/ws` 并通过 `auth` 帧完成认证后，可发送以下 `action` 指令：

| Action | 权限要求 | 参数 | 说明 |
| --- | --- | --- | --- |
| `auth` | 无，按令牌赋予权限 | `token` | 绑定或刷新当前连接的认证身份 |
| `fetch_status` | `dashboard:1` | 无 | 主动拉取一次完整状态 JSON |
| `fetch_logs` | `terminal:1` | 无 | 拉取控制台缓冲区历史日志 |
| `fetch_command_history` | `terminal:1` | 无 | 拉取历史执行指令列表 |
| `fetch_completions` | `terminal:1` | `command`、`requestId` | 请求指令自动补全候选 |
| `execute_command` | `terminal:2` | `command` | 在服务端执行一条控制台指令 |

服务端同时会主动向具备对应权限的连接推送 `status_delta` 状态增量、`new_log` 新增日志以及 `new_monitor_log` 监控采样事件。

## 调用示例

使用 `curl` 获取服务器实时状态：

```bash
curl -s -H "Authorization: Bearer usp_your_token_here" \
  http://127.0.0.1:55533/api/status
```
