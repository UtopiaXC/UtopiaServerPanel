# 游戏内联动设置

「游戏内联动设置」选项卡用于配置网页面板与 Minecraft 服务端游戏内机制的双向交互规则，涵盖白名单联动、服务器重启指令以及网页与游戏内聊天同步。

## 白名单设置

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `serverWhitelistEnabled` | 与服务端同步 | 直接切换 Minecraft 原生白名单开关 |
| `whitelistManagementEnabled` | `false` | 启用网页白名单申请与审核系统；关闭原生白名单时自动关闭此项 |
| `whitelistAllowGuest` | `true` | 允许未登录访客提交白名单申请，同步更新 `guest` 角色的 `whitelist_apply` 权限 |
| `whitelistApplyCooldownSec` | `60` | 同一 IP 提交申请的冷却秒数，范围 `0` 至 `86400` |
| `whitelistMaxPendingPerIp` | `5` | 同一 IP 允许的最大待审核申请数量，范围 `1` 至 `50` |
| `whitelistSendEmail` | `false` | 审核完成后自动发送通知邮件，需先配置 SMTP |
| `whitelistSocialRequired` | `false` | 申请时是否要求填写社交账号 |
| `whitelistRequiredSocialPlatforms` | `[]` | 必填社交平台列表，例如 QQ、Discord |

关于公开申请与管理员审核的完整使用流程，请参阅 [白名单管理](/features/whitelist)。

## 服务器重启设置

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `serverRestartCommand` | 空 | 在网页端触发重启时向服务端发送的命令，留空时默认执行 `stop` |
| `serverRestartHint` | 空 | 管理员点击重启按钮时弹出的确认提示说明 |

由于 Minecraft 模组自身无法在进程退出后重新拉起 JVM，建议配合 Docker `restart: always`、systemd 或专用开服守护脚本使用，当面板执行 `stop` 后由外部守护进程自动重启服务端。

## 聊天与站内信联动

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `chatSyncEnabled` | `true` | 是否开启游戏内聊天与网页聊天室消息同步 |
| `webChatSendEnabled` | `true` | 是否允许网页端用户通过聊天室向游戏内公屏发送消息 |
| `chatRetentionDays` | `30` | 聊天与站内信历史记录保留天数，范围 `1` 至 `3650` |

开启聊天同步后，游戏内玩家发送的聊天消息会实时记录并推送至网页端，网页端发送的消息也会同步广播到游戏内聊天栏。
