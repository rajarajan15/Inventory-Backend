# StockWise Backend

Multi-tenant inventory management API (Spring Boot 3.3, Java 21, PostgreSQL 17). One StockWise super admin creates
client organizations; each organization gets its own portal (`/o/{slug}`), admins and staff, and fully isolated data.
The React frontend lives in `../inventory-frontend`. Product requirements: [PRD.md](PRD.md). Frontend contract:
[FRONTEND_INTEGRATION_PRD.md](FRONTEND_INTEGRATION_PRD.md). Developer guides: [docs/CODEBASE.md](docs/CODEBASE.md)
(architecture and code) and [docs/DATABASE.md](docs/DATABASE.md) (tables and migrations).

## Architecture

| Concern | Approach |
| --- | --- |
| Layers | `controller` (HTTP + validation) → `service` (transactions, business rules) → `repository` (Spring Data JPA) |
| Tenancy | `/api/orgs/{slug}/**` is resolved by `TenantFilter`; every repository query is scoped by organization id, and a user can only reach their own organization |
| Auth | Stateless JWT access tokens (15 min) + hashed, per-session refresh tokens. Roles: `SUPER_ADMIN`, `ADMIN`, `STAFF`. `CurrentUser` is the single place that reads the authenticated user |
| Schema | Flyway migrations in `src/main/resources/db/migration`; Hibernate only validates (`ddl-auto=validate`) |
| Stock integrity | Stock in/out lock the product row; product edits use optimistic locking (`version`) and return `409` on stale data; every quantity change is written to the append-only `stock_movements` ledger |
| Errors | `GlobalExceptionHandler` returns one JSON shape with a user-readable `message`, optional `validationErrors`, and a `requestId` |
| Observability | `X-Request-Id` on every response and in every log line; health probes at `/actuator/health/{liveness,readiness}` |
| Security headers | CSP, `X-Frame-Options: DENY`, HSTS, `nosniff`, `Referrer-Policy: no-referrer`; CORS without credentials |

## Run locally

Prerequisites: Java 21+, PostgreSQL 17 with an empty database `inventory_db`.

1. Put local secrets in `src/main/resources/application-local.properties` (gitignored):
   ```properties
   spring.datasource.password=...
   jwt.secret=<random, at least 32 characters>
   app.super-admin.password=<8-64 chars, upper, lower, number, special character>
   # optional: real email via Gmail SMTP
   app.mail.enabled=true
   spring.mail.password=<Google App Password>
   ```
2. Start: `.\mvnw.cmd spring-boot:run`. Flyway creates or upgrades the schema on startup.
3. Swagger UI: http://localhost:8080/swagger-ui.html (disabled in the `prod` profile unless `API_DOCS_ENABLED=true`).

Set `SEED_DEMO_DATA=true` for a `demo` organization (`admin@demo.com` / `Admin@123`, `staff@demo.com` / `Staff@123`).

### With Docker

```bash
cp .env.example .env   # fill in DB_PASSWORD, JWT_SECRET, SUPER_ADMIN_PASSWORD, ...
docker compose up --build
```

## Configuration

All settings come from environment variables (see [.env.example](.env.example)). Required: `DB_PASSWORD`, `JWT_SECRET`,
and `SUPER_ADMIN_PASSWORD` on the first start. The app refuses to start with a missing, short or sample JWT secret.
Use `SPRING_PROFILES_ACTIVE=prod` for deployments (API docs off, graceful shutdown, proxy headers honoured).

## Database changes

Never edit an applied migration. Add a new file `V{n}__description.sql` in `src/main/resources/db/migration`, update
the entity, and run the tests: they apply every migration to H2 and fail if the entities and schema disagree.
Databases created before Flyway was introduced are adopted automatically as version 1.

## Tests and CI

```bash
.\mvnw.cmd test
```

Unit tests cover services and the password policy; integration tests (`MultiTenantFlowIntegrationTest`,
`SecurityAndErrorHandlingTest`, `InventoryIntegrityTest`) run the full stack on H2, including tenant isolation, error
responses, concurrent stock-outs and stale edits. `.github/workflows/ci.yml` runs the tests and builds the Docker image.
