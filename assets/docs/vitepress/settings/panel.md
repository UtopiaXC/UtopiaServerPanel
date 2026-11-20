# 面板设置

系统设置中的「面板设置」与「面板功能」选项卡集中管理站点基础信息、注册与绑定策略、安全防护、SMTP 邮件服务以及监控与小地图参数。访问需要 `admin:1` 查看权限，修改需要 `admin:2` 完全控制权限。

## 系统信息

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `siteName` | `Utopia Server Panel` | 显示在浏览器标题与导航栏的站点名称 |
| `siteDescription` | `Minecraft Server Management Panel` | 站点简介与公开页面描述 |
| `baseUrl` | 空 | 面板外部访问基础 URL，用于生成邮件激活链接与 Passkey WebAuthn 校验 |
| `defaultLocale` | `zh-CN` | 新访客默认界面语言 |
| `siteFavicon` | 默认图标 | 支持上传自定义 PNG、ICO、JPG、WebP 或 SVG 站点图标，并支持一键恢复默认 |

## 注册与绑定

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `allowRegistration` | `true` | 是否允许访客在登录页自助注册账号 |
| `defaultRegisterRoleId` | `3` | 自助注册账号默认分配的角色 ID |
| `requireEmailVerification` | `false` | 注册后是否要求邮件激活，需先启用并配置 SMTP |
| `requireBindingOnRegister` | `false` | 注册时是否必须提供 `/usp bind` 获取的绑定码 |
| `forcePlayerBinding` | `false` | 是否强制已登录但未绑定游戏角色的普通用户先完成绑定 |

## 安全策略

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `captchaPolicy` | `auto` | 工作量证明验证码策略，可选 `auto`、`always`、`off` |
| `captchaDifficulty` | `80000` | 工作量证明计算难度，范围 `10000` 至 `1000000` |
| `sessionTimeoutDays` | `7` | 登录会话有效期，单位为天，范围 `1` 至 `365` |
| `force2fa` | `false` | 是否强制所有账号启用 TOTP 两步验证 |
| `maxFailedLogins` | `5` | 触发账号锁定的连续失败次数阈值，范围 `3` 至 `50` |
| `lockoutMinutes` | `15` | 触发锁定后的封锁时长，单位为分钟，范围 `1` 至 `1440` |

## SMTP 邮件配置

配置 SMTP 后，面板可支持注册邮件激活、邮箱找回密码、邮箱重置两步验证以及白名单审核结果通知。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `smtpEnabled` | `false` | 是否启用 SMTP 邮件发送 |
| `smtpHost` | 空 | SMTP 服务器主机名 |
| `smtpPort` | `465` | SMTP 服务器端口，范围 `1` 至 `65535` |
| `smtpEncryption` | `ssl` | 加密方式，通常使用 `ssl` 或 `starttls` |
| `smtpUser` | 空 | SMTP 登录用户名 |
| `smtpPassword` | 空 | SMTP 密码，支持留空保持原密码或显式清除 |
| `smtpFrom` | 空 | 发件人邮箱地址 |
| `smtpFromName` | `Utopia Server Panel` | 发件人显示名称 |

配置完成后可直接点击「测试连接」验证 SMTP 参数是否可用。

## 面板功能参数

在「面板功能」选项卡中还包含以下两项核心功能的全局配置：

- **服务器监控**：控制 `monitorEnabled` 采样开关、`monitorInterval` 采样间隔、`monitorRetentionDays` 数据保留天数以及 `monitorShowPlayerNames` 玩家名展示，详见 [监控日志](/features/logs)。
- **网页小地图**：控制 `mapEnabled` 总开关、`mapRenderMode` 渲染模式、`mapMaxChunksPerTick` 每 Tick 扫描区块数、`mapCacheMaxMb` 缓存上限以及 14 项图层权限映射，详见 [小地图](/features/minimap)。
