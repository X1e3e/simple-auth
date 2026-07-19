# Simple Auth (RU/EN)

[Русский](#русский) | [English](#english)

---

## Русский

Легковесный, быстрый и безопасный плагин авторизации для серверов Bukkit/Paper. Поддерживает регистрацию, вход по паролю, сессии автологина по IP и автоматический вход для игроков с лицензией.

### Команды
- `/register <пароль> <повтор_пароль>` — Зарегистрировать новый аккаунт.
- `/login <пароль>` — Войти в существующий аккаунт.
- `/changepassword <старый_пароль> <новый_пароль>` — Сменить пароль аккаунта.

### Настройка конфигурации (config.yml)
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

---

## English

A lightweight, secure, and fast in-game authentication plugin for Bukkit/Paper servers. It provides user registration, login, session management, and optional integration with web frontends.

### Commands
- `/register <password> <confirm_password>` — Register a new account.
- `/login <password>` — Log in to an existing account.
- `/changepassword <old_password> <new_password>` — Change account password.

### Configuration Example (config.yml)
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
