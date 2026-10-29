# FinanTech S.A. — Backend

API Spring Boot (préstamos, pagos, auth, correos).

## Coolify

1. Resource → **Dockerfile** o **Docker Compose**
2. Repo: `https://github.com/zRipWolfx/FinanTech-bk`
3. Base Directory: vacío
4. Dockerfile: `Dockerfile` (o Compose: `docker-compose.yml`)
5. Port: `8080`
6. Env: copiar de `.env.example` (`DB_*`, `MAIL_*`, `FRONTEND_BASE_URL`)
7. Dominio API: `https://app.mtasoftware.cloud`

Health: `GET /api/health`

## Local

```bash
docker compose up -d --build
```
