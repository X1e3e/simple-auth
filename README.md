# Simple Auth

A lightweight, secure, and fast in-game authentication plugin for Bukkit/Paper servers. It provides user registration, login, session management, and optional integration with web frontends.

## Features

- **Secure Passwords:** Hash algorithms including salted SHA-256 and AuthMe-compatible Double SHA-256.
- **IP Autologin:** Remember player IP addresses to skip passwords on reconnect.
- **Premium Auto-login:** Detect premium/licensed players and skip authentication automatically.
- **Player Restrictions:** Restricts movement, chat, interaction, and commands for unauthenticated players.
- **Optional Web API:** Integrated HTTP server for website actions (e.g. changing passwords from a web dashboard).

## Commands

- `/register <password> <confirm_password>` — Register a new account.
- `/login <password>` — Log in to an existing account.
- `/changepassword <old_password> <new_password>` — Change account password.

## Configuration

Settings are controlled in `config.yml`:

```yaml
database:
  type: "sqlite" # SQLite or MySQL
  sqlite:
    file: "database.db"

password:
  algorithm: "SHA256"
  min-length: 6

ip-autologin:
  enabled: true
  session-hours: 72

security:
  timeout-seconds: 60
  max-attempts: 3
  apply-blindness: true
  premium-auto-login: true

api:
  enabled: false
  port: 8080
  key: "change-me-to-a-very-secure-secret-key"
```
