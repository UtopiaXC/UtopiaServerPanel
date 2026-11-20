# API Reference

Utopia Server Panel exposes a complete HTTP REST API and WebSocket real-time channel supporting Personal API Access Tokens for automation scripts, external monitoring, and third-party integrations.

## Authentication & Token Format

Logged-in users can create Personal API Access Tokens under **Personal Settings -> API Tokens**. Tokens start with the `usp_` prefix and are displayed in full only once upon creation; only their SHA-256 hash is stored in the database.

Pass the token in the `Authorization` header for HTTP requests:

```http
Authorization: Bearer usp_your_token_here
```

For WebSocket connections to `/ws`, send an authentication frame after connecting:

```json
{
  "action": "auth",
  "token": "usp_your_token_here"
}
```

## Scopes & Permission Rules

The effective permissions of an API token are the intersection of **the owner's role permissions** and **the token's `scopes`**:

- A token with scope `*` inherits all permissions of the owning account.
- A token with restricted scopes such as `dashboard:1,terminal:2` can only access matching modules up to the specified level.
- Sensitive account credential and binding mutations, including non-`GET` requests under `/api/me/*` and `/api/binding/*`, strictly require `*` scope and return `INSUFFICIENT_SCOPE` for restricted tokens.

## HTTP Endpoints

Below are the endpoints accessible with an API Access Token, grouped by module.

### Profile & Self-Service

| Method | Path | Permission or Scope | Description |
| --- | --- | --- | --- |
| `GET` | `/api/me` | Authenticated; restricted tokens need `profile:1` or `read` | Get current account info and permission matrix |
| `GET` | `/api/me/sessions` | Same as above | List active login sessions |
| `GET` | `/api/me/login-history` | Same as above | Retrieve the latest 20 login audit entries |
| `GET` | `/api/me/tokens` | Same as above | List API token metadata for the current user |
| `POST` | `/api/me/tokens` | `*` scope only | Create a new API access token |
| `DELETE` | `/api/me/tokens/{id}` | `*` scope only | Revoke an API access token |
| `GET` | `/api/binding/list` | Authenticated; restricted tokens need `profile:1` or `read` | List bound Minecraft characters |
| `GET` | `/api/player/me` | Same as above | Fetch live in-game data for the primary bound player |

### Server Status & Monitoring

| Method | Path | Permission or Scope | Description |
| --- | --- | --- | --- |
| `GET` | `/api/status` | `dashboard:1` | Get live server status, TPS, MSPT, players, and system resources |
| `GET` | `/api/monitor/config` | Public or authenticated | Get monitoring display configuration |
| `GET` | `/api/monitor/perf` | `logs:1` | Query historical performance samples |
| `GET` | `/api/monitor/players` | `logs:1` | Query player join and leave events |
| `GET` | `/api/monitor/lifecycle` | `logs:1` | Query paginated server lifecycle records |
| `GET` | `/api/monitor/lifecycle/range` | `logs:1` | Query server lifecycle records within a time range |

### Performance Profiler & Web Minimap

| Method | Path | Permission or Scope | Description |
| --- | --- | --- | --- |
| `GET` | `/api/profiler/snapshot` | `profiler:1` | Get current dimension tick breakdown and chunk hotspots |
| `POST` | `/api/profiler/teleport` | `profiler:2` | Teleport bound player to hotspot coordinates |
| `GET` | `/api/map/bootstrap` | `map:1` | Get minimap configuration, dimensions, and permissions |
| `GET` | `/api/map/tile` | `map:1` | Fetch a map tile image for a dimension and zoom level |
| `GET` | `/api/map/regions` | `map:1` | List available or rendered region coordinates |
| `GET` | `/api/map/overlays` | `map:1` | Fetch live players, mobs, entities, and waypoint overlays |
| `POST` | `/api/map/waypoints` | `map:2` | Create a map waypoint |
| `DELETE` | `/api/map/waypoints/{id}` | `map:2` | Delete a map waypoint |
| `POST` | `/api/map/teleport` | `map:1` and map teleport setting | Teleport bound player to map coordinates |
| `POST` | `/api/map/reset-health` | `admin:2` | Reset minimap renderer health state |
| `POST` | `/api/map/clear-cache` | `admin:2` | Clear minimap disk tile cache |

### Mods & Server Config

| Method | Path | Permission or Scope | Description |
| --- | --- | --- | --- |
| `GET` | `/api/mods/overview` | `mods_config:1` | List installed mods and config file tree |
| `GET` | `/api/mods/config` | `mods_config:1` | Read a mod configuration file |
| `PUT` | `/api/mods/config` | `mods_config:2` | Save edits to a mod configuration file |
| `GET` | `/api/mods/server-properties` | `mods_config:1` | Read `server.properties` |
| `PUT` | `/api/mods/server-properties` | `mods_config:2` | Save changes to `server.properties` |
| `POST` | `/api/mods/restart` | `mods_config:2` | Trigger the configured server restart command |

### Whitelist & Messaging

| Method | Path | Permission or Scope | Description |
| --- | --- | --- | --- |
| `GET` | `/api/whitelist/admin/applications` | `whitelist_manage:1` | List whitelist applications |
| `POST` | `/api/whitelist/admin/applications/{id}/review` | `whitelist_manage:2` | Approve or reject a whitelist application |
| `GET` | `/api/whitelist/admin/entries` | `whitelist_manage:1` | List current server whitelist entries |
| `POST` | `/api/whitelist/admin/entries` | `whitelist_manage:2` | Manually add a whitelist entry |
| `DELETE` | `/api/whitelist/admin/entries/{name}` | `whitelist_manage:2` | Manually remove a whitelist entry |
| `GET` | `/api/messages/inbox` | Authenticated; restricted tokens need `messages:1` or `chat:1` | List inbox messages |
| `GET` | `/api/messages/sent` | Same as above | List sent messages |
| `GET` | `/api/messages/unread-count` | Same as above | Get unread message count |
| `POST` | `/api/messages/send` | Authenticated; restricted tokens need `messages:2` or `chat:2` | Send an in-site message |
| `POST` | `/api/messages/read-all` | Same as above | Mark all inbox messages as read |
| `POST` | `/api/messages/{id}/read` | Same as above | Mark a single message as read |
| `GET` | `/api/chat/history` | Authenticated; restricted tokens need `chat:1` or `messages:1` | Fetch synced in-game chat history |
| `POST` | `/api/chat/send` | Authenticated; restricted tokens need `chat:2` or `messages:2` | Send a chat message into the Minecraft server |

### Administration

| Method | Path | Permission or Scope | Description |
| --- | --- | --- | --- |
| `GET` | `/api/admin/users` | `admin:1` | List all panel users |
| `POST` | `/api/admin/users` | `admin:2` | Create a new user |
| `PATCH` | `/api/admin/users/{id}` | `admin:2` | Update user info or status |
| `DELETE` | `/api/admin/users/{id}` | `admin:2` | Delete a user |
| `POST` | `/api/admin/users/{id}/unbind` | `admin:2` | Unbind a user's Minecraft player |
| `POST` | `/api/admin/users/{id}/reset-2fa` | `admin:2` | Reset a user's 2FA configuration |
| `POST` | `/api/admin/users/{id}/unlock` | `admin:2` | Unlock a locked-out account |
| `GET` | `/api/admin/roles` | `admin:1` | List all roles |
| `GET` | `/api/admin/roles/{id}` | `admin:1` | Get role details and permission matrix |
| `POST` | `/api/admin/roles` | `admin:2` | Create a custom role |
| `PUT` | `/api/admin/roles/{id}` | `admin:2` | Update role permissions |
| `DELETE` | `/api/admin/roles/{id}` | `admin:2` | Delete a custom role |
| `GET` | `/api/admin/permissions` | `admin:1` | List all permission definitions |
| `GET` | `/api/admin/settings` | `admin:1` | Get global system settings |
| `PATCH` | `/api/admin/settings` | `admin:2` | Update global system settings |
| `GET` | `/api/admin/worlds` | `admin:1` | List indexed saves and database stats |

## WebSocket Reference

After connecting to `/ws` and authenticating with an `auth` frame, the following `action` commands are supported:

| Action | Permission | Parameters | Description |
| --- | --- | --- | --- |
| `auth` | None | `token` | Authenticate or refresh the connection identity |
| `fetch_status` | `dashboard:1` | None | Request a full server status JSON snapshot |
| `fetch_logs` | `terminal:1` | None | Fetch buffered console log lines |
| `fetch_command_history` | `terminal:1` | None | Fetch executed command history |
| `fetch_completions` | `terminal:1` | `command`, `requestId` | Request Tab completion candidates |
| `execute_command` | `terminal:2` | `command` | Execute a command on the server console |

The server also pushes `status_delta`, `new_log`, and `new_monitor_log` events to authorized connections.

## Example Request

Fetch live server status with `curl`:

```bash
curl -s -H "Authorization: Bearer usp_your_token_here" \
  http://127.0.0.1:55533/api/status
```
