# In-Game Integration

The In-Game Integration tab configures bidirectional behavior between the web panel and the Minecraft server, covering whitelist synchronization, server restart commands, and web-to-game chat sync.

## Whitelist Settings

| Option | Default | Description |
| --- | --- | --- |
| `serverWhitelistEnabled` | Synced with server | Directly toggles the native Minecraft server whitelist |
| `whitelistManagementEnabled` | `false` | Enables the web whitelist application and review workflow; disabled automatically if native whitelist is off |
| `whitelistAllowGuest` | `true` | Allows unauthenticated guests to submit applications by updating `guest` role `whitelist_apply` permission |
| `whitelistApplyCooldownSec` | `60` | Per-IP application cooldown in seconds, from `0` to `86400` |
| `whitelistMaxPendingPerIp` | `5` | Maximum concurrent pending applications per IP, from `1` to `50` |
| `whitelistSendEmail` | `false` | Sends review outcome emails automatically; requires SMTP |
| `whitelistSocialRequired` | `false` | Requires social platform handles on applications |
| `whitelistRequiredSocialPlatforms` | `[]` | List of required social platform names such as Discord |

For the full application and review workflow, see [Whitelist Management](/en/features/whitelist).

## Server Restart Settings

| Option | Default | Description |
| --- | --- | --- |
| `serverRestartCommand` | Empty | Command sent to the server when restarting from the web panel; defaults to `stop` when empty |
| `serverRestartHint` | Empty | Confirmation hint displayed to administrators before triggering a restart |

Because a Minecraft mod cannot relaunch the JVM after process exit, pair this with Docker `restart: always`, systemd, or a process supervisor so the server restarts automatically after `stop`.

## Chat & In-Site Message Sync

| Option | Default | Description |
| --- | --- | --- |
| `chatSyncEnabled` | `true` | Enables synchronization between in-game chat and the web chat room |
| `webChatSendEnabled` | `true` | Allows web users to send messages into in-game chat |
| `chatRetentionDays` | `30` | Retention period in days for chat and in-site messages, from `1` to `3650` |

When enabled, in-game player messages are recorded and pushed to the web chat room, and web chat messages are broadcast in-game.
