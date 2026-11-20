# Panel Settings

The Panel Settings and Panel Features tabs in System Settings manage basic site metadata, registration and binding rules, security policies, SMTP email delivery, and monitoring and minimap parameters. Viewing requires `admin:1` and modifying requires `admin:2`.

## System Info

| Option | Default | Description |
| --- | --- | --- |
| `siteName` | `Utopia Server Panel` | Site title shown in the browser tab and navigation bar |
| `siteDescription` | `Minecraft Server Management Panel` | Site summary and public page description |
| `baseUrl` | Empty | External base URL used for email activation links and WebAuthn Passkey verification |
| `defaultLocale` | `zh-CN` | Default UI language for new visitors |
| `siteFavicon` | Default icon | Upload a custom PNG, ICO, JPG, WebP, or SVG favicon, or reset to default |

## Registration & Binding

| Option | Default | Description |
| --- | --- | --- |
| `allowRegistration` | `true` | Allows visitors to self-register on the login page |
| `defaultRegisterRoleId` | `3` | Default role ID assigned to self-registered accounts |
| `requireEmailVerification` | `false` | Requires email activation after registration; requires SMTP to be enabled and configured |
| `requireBindingOnRegister` | `false` | Requires a `/usp bind` code during registration |
| `forcePlayerBinding` | `false` | Forces logged-in regular users without a bound character to bind one first |

## Security Policy

| Option | Default | Description |
| --- | --- | --- |
| `captchaPolicy` | `auto` | Proof-of-Work captcha policy: `auto`, `always`, or `off` |
| `captchaDifficulty` | `80000` | Proof-of-Work difficulty from `10000` to `1000000` |
| `sessionTimeoutDays` | `7` | Login session lifetime in days, from `1` to `365` |
| `force2fa` | `false` | Enforces TOTP two-factor authentication for all accounts |
| `maxFailedLogins` | `5` | Consecutive failed login attempts before account lockout, from `3` to `50` |
| `lockoutMinutes` | `15` | Lockout duration in minutes, from `1` to `1440` |

## SMTP Email Configuration

Configuring SMTP enables registration activation emails, password recovery, 2FA reset emails, and whitelist review notifications.

| Option | Default | Description |
| --- | --- | --- |
| `smtpEnabled` | `false` | Enables SMTP email delivery |
| `smtpHost` | Empty | SMTP server hostname |
| `smtpPort` | `465` | SMTP server port, from `1` to `65535` |
| `smtpEncryption` | `ssl` | Transport encryption such as `ssl` or `starttls` |
| `smtpUser` | Empty | SMTP authentication username |
| `smtpPassword` | Empty | SMTP password; leave blank to keep existing or clear explicitly |
| `smtpFrom` | Empty | Sender email address |
| `smtpFromName` | `Utopia Server Panel` | Sender display name |

Use the Test Connection button to verify SMTP settings before saving.

## Panel Feature Settings

The Panel Features tab also configures two core modules:

- **Monitoring**: Configures `monitorEnabled`, `monitorInterval`, `monitorRetentionDays`, and `monitorShowPlayerNames`. See [Monitoring Logs](/en/features/logs).
- **Web Map**: Configures `mapEnabled`, `mapRenderMode`, `mapMaxChunksPerTick`, `mapCacheMaxMb`, and the 14 per-layer permission rules. See [Web Minimap](/en/features/minimap).
