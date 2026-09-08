# Repository Guidelines

## Project Structure & Module Organization

`backend/` is a Java 17 Maven multi-module Spring Boot application. `continew-server` contains the entry point, controllers, runtime profiles, and Liquibase changelogs; `continew-eve` contains EVE domain services and models; `continew-common`, `continew-system`, and `continew-plugin` provide shared platform features. Add EVE migrations under `backend/continew-server/src/main/resources/db/changelog/mysql/eve/` and register them in the master changelog. `frontend/` is the Node.js 22 Vue 3/Vite client; EVE pages and API clients are in `src/views/eve/` and `src/apis/eve/`. Documentation belongs in `docs/`; task-specific scripts belong in numbered subfolders under `scripts/`.

## Local Setup, Build, and Development

Use the locally installed MySQL at `127.0.0.1:3306` and Redis database 14. Create a dedicated MySQL database, for example:

```bash
mysql -h127.0.0.1 -P3306 -uroot -p -e "CREATE DATABASE eve_corp_manager CHARACTER SET utf8mb4"
python3 scripts/init_local.py
```

Update the generated `.env` so `DB_NAME=eve_corp_manager` and `REDIS_DB=14`, using your local credentials. Never commit `.env` or `frontend/.env.local`.

- `cd backend && mvn -B -ntp -Pfat_jar -pl continew-server -am -Dspotless.apply.skip=true package` builds and tests the backend fat JAR.
- `python3 scripts/dev_backend.py` runs the built backend on port 8000 with `dev,local` profiles.
- `cd frontend && nvm use && corepack enable && pnpm install --frozen-lockfile` installs the pinned frontend toolchain and dependencies.
- `pnpm exec vite --host 127.0.0.1` runs the frontend on port 5173.
- `pnpm lint && pnpm typecheck && pnpm build` performs frontend quality checks.
- `scripts/g008/01-run-integration-tests.sh` runs real MySQL/Redis integration tests after supplying the documented `G008_IT_*` variables.

## Coding Style & Testing

Spotless enforces the repository P3C Java style; ESLint formats and checks Vue/TypeScript. Use `PascalCase` for Java types and Vue components, and `camelCase` for methods, fields, utilities, and API modules. New or changed backend classes and non-trivial methods require concise Simplified Chinese comments. Put Java tests in `src/test/java`; name database integration tests `*IT`. Add focused regression tests and never edit an applied Liquibase changeset.

## Commits & Pull Requests

Use Lore-style commits: `<tag>: <中文摘要>` followed by an indented Chinese description, for example `feat: 添加权限刷新\n  支持主动同步游戏权限`. Keep commits focused. Pull requests must describe user-visible behavior, validation commands, linked issues or design documents, and configuration or migration impact; include screenshots for UI changes. Never include credentials, tokens, private keys, or real corporation data.
