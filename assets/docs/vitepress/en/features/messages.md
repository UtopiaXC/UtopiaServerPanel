# Messages & Chat Sync

The Messages & Chat Sync module combines an in-site inbox, offline player-to-player mail, and bidirectional synchronization between the web chat room and Minecraft in-game chat for authenticated panel users.

## In-Site Inbox & Offline Mail

Each user has an Inbox and Sent view for system notifications and direct messages.

### Message Types & Notifications

- **Direct & In-Game Mail**: Supports subject and message body; messages sent from the web are typed as `private`, while messages sent from in-game commands are typed as `ingame_mail`.
- **Whitelist Notifications**: Administrators receive `whitelist_submitted` notifications when new whitelist applications arrive, and registered applicants receive `whitelist_review` notifications upon approval or rejection.
- **Real-Time Updates & Actions**: Filter the inbox by message type, mark individual or all messages as read, and reply directly to senders. Incoming messages push unread count updates over WebSocket.

### Cross-Platform Delivery & In-Game Commands

When composing a message on the web, recipients can be specified by either panel username or bound Minecraft player name:

1. If the target player is currently online, the message is pushed live to their in-game chat alongside saving it to their inbox.
2. If the target player is offline, the message remains queued in the database until they check it in-game or on the web panel.
3. In-game players can run `/usp msg <target> <message>` to send mail to another player or panel account.
4. In-game players can run `/usp inbox` to read up to 8 unread inbox messages and mark them as delivered.

## In-Game Chat Sync

Chat Sync bridges the web chat room with Minecraft public server chat and persists chat history.

- **Game-to-Web Sync**: In-game public chat messages are persisted to the database and pushed live to the web public chat room via WebSocket.
- **Web-to-Game Chat**: Authenticated users with at least one bound Minecraft character can select which bound character to speak as from the web chat room. Messages up to `256` characters are broadcast into the server with a `WEB` badge on the web view.
- **Whisper Channel Auditing**: Chat history supports filtering by `public` and `whisper` channels. Regular users can only view whispers they participated in, while administrators with `admin:1` can audit all records.

## Related Settings

Chat sync toggles and retention limits are configured under **System Settings -> In-Game Integration -> Chat & In-Site Messages**. See [In-Game Integration](/en/settings/ingame):

| Option | Default | Description |
| --- | --- | --- |
| `chatSyncEnabled` | `true` | Enables syncing and live pushing of in-game chat messages |
| `webChatSendEnabled` | `true` | Allows bound web users to send public chat messages into the server |
| `chatRetentionDays` | `30` | Retention period in days for chat and in-site messages, from `1` to `3650` |
