# StockWise Inventory Backend - Product Requirements Document

## Document Control

| Field | Value |
| --- | --- |
| Status | Living implementation baseline |
| Version | 2.2 |
| Last reviewed | 2026-09-29 |
| Product | StockWise multi-organization inventory REST API |
| Source of truth | Current repository implementation, tests, and configuration |

This document describes behavior that is present in the repository, not aspirational scope. It is the change-verification baseline for this project.

## Required PRD Maintenance

Every change to this repository must include a PRD review before completion.

1. Identify the affected requirement IDs, API contract, data model, configuration, security rule, setup step, or test coverage below.
2. Update the affected sections so they match the delivered implementation. Add a requirement ID when new behavior is introduced.
3. Add one row to the Change Record with the change, affected IDs, and verification performed.
4. Run the relevant checks in the Verification Matrix. Do not mark the change complete while the PRD describes obsolete behavior.

For a non-functional internal-only change, record it in the Change Record and state that no product requirement changed. For a documentation-only change, update this document when the project baseline or its verification guidance changes.

## Product Summary

StockWise is a multi-organization (multi-tenant) inventory platform. Any number of client organizations use it, each through its own portal URL (`{frontend}/o/{slug}`), and each organization's data (users, categories, products, stock) is isolated from every other organization's.

The platform is run by one **super admin** (the StockWise owner), who reviews subscription requests from prospective clients, creates organizations (and so their URLs), and invites each organization's first admin by email. The **organization admin** manages that organization's data, imports existing data from CSV, and approves users who sign up on the portal. JWT header bearer authentication (`Authorization: Bearer <token>`) is used throughout. PostgreSQL is the production persistence target; H2 is used by tests.

### Users and Roles

| Role | Scope | Intended capabilities |
| --- | --- | --- |
| `SUPER_ADMIN` | Platform (exactly one, seeded) | Review/reject subscription requests, create organizations, activate/suspend organizations, invite organization admins, list organization admins. **No access to any organization's inventory data.** |
| `ADMIN` | One organization | Organization setup and CSV import, product/category CRUD, stock adjustments, approve/reject sign-ups, invite/disable/enable members |
| `STAFF` | One organization | Product/category reads, organization profile view, stock adjustments |

### User lifecycle (`users.status`)

| Status | Meaning |
| --- | --- |
| `PENDING` | Self-registered on the portal; cannot log in until an org admin approves |
| `ACTIVE` | Can log in; created by approval or by accepting an invitation |
| `REJECTED` | Sign-up rejected by an org admin; cannot log in (can later be approved) |
| `DISABLED` | Blocked by an org admin; existing access tokens stop working immediately |

## Implemented Requirements

| ID | Requirement | Acceptance criteria |
| --- | --- | --- |
| PRD-TEN-01 | Organizations are isolated tenants addressed by URL slug. | All tenant endpoints live under `/api/orgs/{slug}/**`. `TenantFilter` resolves the slug: unknown → `404`, suspended → `403`. An authenticated caller whose organization differs from the slug (including the super admin) gets `403`. Every tenant repository query is additionally filtered by the organization id from the request context, so IDs from another organization return `404`. |
| PRD-TEN-02 | Uniqueness is per organization. | Category names (case-insensitive) and product SKUs are unique within an organization; different organizations may reuse them. User email is globally unique (one account belongs to one organization). |
| PRD-SUB-01 | Prospective clients can request StockWise. | `POST /api/public/subscription-requests` stores a `PENDING` request, emails StockWise (`app.platform.notification-email`) and acknowledges the requester by email. |
| PRD-PLAT-01 | The super admin manages the platform. | `POST /api/platform/auth/login` authenticates only the `SUPER_ADMIN`. All other `/api/platform/**` endpoints require `SUPER_ADMIN`. The super admin lists/rejects subscription requests (rejection emails the requester), creates organizations with a unique, non-reserved slug (3-63 chars, `a-z0-9-`), optionally approving a subscription request and inviting an admin, lists organizations and their admins, and activates/suspends organizations. |
| PRD-PLAT-02 | Exactly one super admin exists. | The super admin is seeded at startup from `SUPER_ADMIN_EMAIL`/`SUPER_ADMIN_PASSWORD` if none exists. No API creates or grants `SUPER_ADMIN`. |
| PRD-INV-01 | Accounts are provisioned by emailed, single-use invitations. | Invitations carry an organization, role (`ADMIN` or `STAFF` only) and a random 256-bit token, expire after `app.invitation.expiry-days` (default 7), and are emailed as `{frontend}/invite/{token}`. `GET /api/public/invitations/{token}` returns invitation details; `POST /api/public/invitations/{token}/accept` with `{ name, password }` creates an `ACTIVE` user in that organization. Expired/used tokens return `400`. |
| PRD-AUTH-01 | Organization users register and authenticate on their portal. | `POST /api/orgs/{slug}/auth/register` creates a `PENDING` `STAFF` user (role cannot be chosen), emails the organization's active admins and acknowledges the user; returns `202`. `POST /api/orgs/{slug}/auth/login` succeeds only for `ACTIVE` members of that organization. Wrong password, unknown email or membership of another organization → `401` with the same message; correct password but `PENDING`/`REJECTED`/`DISABLED` → `403` with an explanatory message. |
| PRD-AUTH-02 | The API maintains a JWT access-token and refresh-token lifecycle via Authorization headers. | Access tokens carry the email subject and `role`, `userId`, `name`, and (for org users) `orgId`, `orgSlug` claims. Access tokens default to 15 minutes; refresh tokens to 7 days. Every login creates its own refresh-token session, so signing in on another tab or device never signs out existing sessions; only the SHA-256 hash of a refresh token is stored. `POST /api/auth/refresh` (JSON body only) refuses users who are no longer `ACTIVE` or whose organization is suspended; error messages never echo the token. `POST /api/auth/logout` revokes that session; disabling a user deletes all of their sessions. Tokens are accepted only in the `Authorization` header, never from cookies. Tokens of non-`ACTIVE` users are ignored by the JWT filter. |
| PRD-SEC-02 | Passwords follow one policy everywhere. | Registration and invitation acceptance require 8-64 characters with an uppercase letter, a lowercase letter, a number and a special character, and no spaces. The validation message lists exactly which rules are unmet (field `password`). The super admin password is checked against the same policy when it is first seeded; startup fails if it is missing or weak. |
| PRD-SEC-03 | The service is hardened against CSRF, XSS and secret exposure. | CSRF: no cookie authentication exists (header-only Bearer tokens), CORS allows only configured origins without credentials. Headers: `Content-Security-Policy` (`frame-ancestors 'none'`), `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, HSTS on HTTPS. Secrets: `JWT_SECRET` has no default and must be at least 32 bytes; startup fails if it is missing, short or the previously committed sample key. No password defaults are committed; local secrets live in git-ignored `application-local.properties`. Email subjects are stripped of CR/LF. Unexpected errors return a generic message with a reference id; details are only logged. |
| PRD-SEC-01 | Protected API operations enforce role-based access. | Public: `/api/public/**`, `/api/auth/**`, `/api/platform/auth/login`, `/api/orgs/*/auth/**`, health, OpenAPI, Swagger UI. `/api/platform/**` requires `SUPER_ADMIN`. Within a tenant: product/category reads, organization profile and stock adjustments allow `ADMIN` or `STAFF`; organization setup, CSV import, product/category writes and all `/users` endpoints require `ADMIN`. |
| PRD-USER-01 | Organization admins manage members. | `GET /api/orgs/{slug}/users[?status=]` and `GET .../users/{id}` return id, name, email, role, status and creation time (never passwords). `POST .../users/{id}/approve` (PENDING/REJECTED → ACTIVE, emails user), `.../reject` (PENDING → REJECTED, emails user), `.../disable`, `.../enable`, and `POST .../users/invite` (role defaults to `STAFF`). An admin cannot disable themself. |
| PRD-ORG-01 | Organization admins complete first-login setup. | `GET /api/orgs/{slug}/organization` returns the organization profile including `setupCompleted` and `portalUrl`. `POST .../organization/setup` with `{ hasExistingData }`: `false` marks setup complete (new organization starts creating data); `true` leaves it open until a CSV import. |
| PRD-ORG-02 | Organization admins import their own existing data via CSV. | `POST /api/orgs/{slug}/organization/import-csv` (multipart field `file`, max 10MB) imports categories and products into the caller's organization only, auto-creating referenced categories, and emails invitations to listed team members (no default passwords are created). Duplicate/invalid rows are skipped with line-numbered warnings. RFC-4180 quoting (including `""`) is supported. Setup is marked complete. The super admin has no import endpoint. |
| PRD-CAT-01 | Administrators manage product categories. | Organization members can list and retrieve categories. Admins can create, update, and delete them. Category names are required, 2-100 characters, and unique per organization. Deletion fails with `409` while products are assigned. The list returns each category's product count from one grouped query and is sorted by name. |
| PRD-PROD-01 | Authorized users can retrieve inventory products. | `GET /api/orgs/{slug}/products` returns a page `{ content, page, size, totalElements, totalPages }` and accepts optional `search` (case-insensitive name or SKU, max 100 characters), `categoryId`, `page` (default 0), `size` (default 20, max 100) and `sort` (`name`, `sku`, `price`, `quantity`, `createdAt`, `updatedAt`, optionally `,asc`/`,desc`; anything else returns `400`). `GET .../products/summary` returns `{ totalProducts, totalUnits, inventoryValue, lowStockCount }` computed in the database. `GET .../products/{id}` returns one product including its `version`. Missing resources return `404`. |
| PRD-PROD-02 | Administrators manage products. | Admins can create, update, and delete products. Product name, SKU, non-negative price, non-negative quantity/minimum stock, and a category of the same organization are required. SKUs are stored uppercase and unique per organization; duplicates return `409`. Updates may send the `version` they were based on; if the product changed since, the update is rejected with `409` and nothing is overwritten. Changing `quantity` through an update is recorded as an `ADJUSTMENT` movement. |
| PRD-STOCK-01 | Authorized users adjust stock safely. | `POST .../products/{id}/stock/in` increases quantity by a positive integer. `POST .../products/{id}/stock/out` decreases it only when sufficient inventory exists. Invalid quantities and insufficient stock return `400`. Stock operations lock the product row, so concurrent operations are applied one after another and stock can never go negative. Every quantity change (opening stock, stock in/out, adjustment, CSV import) is appended to `stock_movements` with the signed change, resulting quantity, notes (max 500), user and time; `GET .../products/{id}/movements?page=&size=` returns it newest first. |
| PRD-STOCK-02 | Users can monitor low stock. | `GET .../products/low-stock` returns the organization's products whose `quantity <= minimumStock`. Product responses include the computed `lowStock` flag. |
| PRD-MAIL-01 | Notification emails are sent through Gmail SMTP. | With `MAIL_ENABLED=true` emails are sent asynchronously via `spring.mail.*` (smtp.gmail.com:587, STARTTLS, App Password). With `MAIL_ENABLED=false` (default) email content, including invitation links, is written to the log. Send failures are logged and never fail the business operation. |
| PRD-OPS-01 | The service exposes operational and API documentation endpoints. | `GET /api/health` returns `UP`, service name, version, and a timestamp. `/actuator/health`, `/actuator/health/liveness` and `/actuator/health/readiness` are public and show only the status (email is excluded, so an SMTP outage does not mark the API unhealthy); no other actuator endpoint is exposed. Swagger UI is at `/swagger-ui.html`; OpenAPI JSON is at `/v3/api-docs` (off in the `prod` profile unless `API_DOCS_ENABLED=true`). |
| PRD-OPS-02 | Requests are traceable. | Every response has an `X-Request-Id` header (a safe incoming value, 8-64 of `A-Za-z0-9._-`, is reused; anything else is replaced). The same id is in every log line of the request and in error bodies as `requestId`. |
| PRD-OPS-03 | The schema is versioned. | Flyway applies `src/main/resources/db/migration` on startup; Hibernate validates the entities against the schema and the app does not start on a mismatch. Databases created before Flyway are adopted as version 1. |
| PRD-ERR-01 | Client-facing failures use a consistent JSON error shape with user-readable messages. | Every failure returns `{ timestamp, status, error, message, path, requestId, validationErrors? }`. Covered: validation (400, per-field `validationErrors`; single-field failures repeat the field message in `message`), malformed JSON (400), invalid enum values in body or query (400, lists allowed values), non-numeric path ids (400), missing parameters or upload file (400), non-CSV upload (400), upload over 10 MB (413), wrong HTTP method (405), unsupported content type (415), unknown endpoint (404), duplicates, stale edits and constraint conflicts (409), bad credentials / expired session (401), permission and account-status errors (403), business rules (400/404), and unexpected errors (500 with a support reference, no internal details). |

## API Contract and Authorization

### Public

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/api/health` | Public | Health status |
| POST | `/api/public/subscription-requests` | Public | Request StockWise for an organization |
| GET | `/api/public/organizations/{slug}` | Public | `{ name, slug }` of an ACTIVE organization (portal landing), else `404` |
| GET | `/api/public/invitations/{token}` | Public | Invitation details |
| POST | `/api/public/invitations/{token}/accept` | Public | `{ name, password }` → activates the account |
| POST | `/api/auth/refresh` | Public | Refresh access token |
| POST | `/api/auth/logout` | Public | Revoke refresh token |

### Super admin portal

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| POST | `/api/platform/auth/login` | Public | Super admin login |
| GET | `/api/platform/subscription-requests[?status=]` | SUPER_ADMIN | List requests |
| POST | `/api/platform/subscription-requests/{id}/reject` | SUPER_ADMIN | `{ note? }` Reject and email requester |
| POST | `/api/platform/organizations` | SUPER_ADMIN | `{ name, slug, description?, contactEmail?, adminName?, adminEmail?, subscriptionRequestId? }` |
| GET | `/api/platform/organizations`, `/api/platform/organizations/{id}` | SUPER_ADMIN | List / get organizations |
| PATCH | `/api/platform/organizations/{id}/status` | SUPER_ADMIN | `{ status: ACTIVE \| SUSPENDED }` |
| GET | `/api/platform/organizations/{id}/admins` | SUPER_ADMIN | Organization admins |
| POST | `/api/platform/organizations/{id}/admin-invitations` | SUPER_ADMIN | `{ name?, email }` Invite an admin |

### Organization portal (`{slug}` must be the caller's own organization)

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| POST | `/api/orgs/{slug}/auth/register` | Public | Self-register (PENDING) |
| POST | `/api/orgs/{slug}/auth/login` | Public | Login (ACTIVE members only) |
| GET | `/api/orgs/{slug}/organization` | ADMIN, STAFF | Organization profile and setup status |
| POST | `/api/orgs/{slug}/organization/setup` | ADMIN | `{ hasExistingData }` |
| POST | `/api/orgs/{slug}/organization/import-csv` | ADMIN | CSV import |
| GET | `/api/orgs/{slug}/categories`, `.../categories/{id}` | ADMIN, STAFF | Read categories |
| POST, PUT, DELETE | `/api/orgs/{slug}/categories`, `.../categories/{id}` | ADMIN | Manage categories |
| GET | `/api/orgs/{slug}/products` (paged), `.../products/{id}`, `.../products/low-stock`, `.../products/summary` | ADMIN, STAFF | Read inventory, totals and alerts |
| GET | `/api/orgs/{slug}/products/{id}/movements` | ADMIN, STAFF | Stock history (paged) |
| POST, PUT, DELETE | `/api/orgs/{slug}/products`, `.../products/{id}` | ADMIN | Manage products |
| POST | `/api/orgs/{slug}/products/{id}/stock/in`, `.../stock/out` | ADMIN, STAFF | Adjust stock |
| GET | `/api/orgs/{slug}/users[?status=]`, `.../users/{id}` | ADMIN | Read members |
| POST | `/api/orgs/{slug}/users/{id}/approve`, `/reject`, `/disable`, `/enable` | ADMIN | Member lifecycle |
| POST | `/api/orgs/{slug}/users/invite` | ADMIN | `{ name?, email, role? }` Invite a member |

## Data Model

| Entity/table | Key fields and rules |
| --- | --- |
| `organizations` | `id`, name, unique `slug`, description, `contact_email`, `status` (`ACTIVE`/`SUSPENDED`), `setup_completed`, created/updated timestamps |
| `users` | `id`, name, globally unique email, BCrypt password, role (`SUPER_ADMIN`/`ADMIN`/`STAFF`), `status`, `organization_id` (null only for the super admin), created timestamp |
| `categories` | `id`, `organization_id`, name (unique per organization), optional description |
| `products` | `id`, `organization_id`, name, description, SKU (unique per organization), decimal price (12,2), quantity, minimum stock, nullable category, `version` (optimistic lock), created/updated timestamps |
| `stock_movements` | Append-only: `organization_id`, `product_id` (set null when the product is deleted), product SKU/name copies, `type` (`INITIAL`/`STOCK_IN`/`STOCK_OUT`/`ADJUSTMENT`/`IMPORT`), signed `quantity_change`, `quantity_after`, notes, `performed_by_id`/`performed_by_name`, created timestamp |
| `subscription_requests` | `id`, organization name, contact name/email/phone, `has_existing_data`, message, `status` (`PENDING`/`APPROVED`/`REJECTED`), review note, created `organization_id` when approved, created/reviewed timestamps |
| `invitations` | `id`, unique token, email, name, role, `organization_id`, `expires_at`, `accepted_at`, created timestamp |
| `refresh_tokens` | One row per login session: `user_id` (required), SHA-256 hash of the token (unique), expiry, revoked flag, created timestamp |

The schema is defined by Flyway migrations in `src/main/resources/db/migration` (`V1__baseline.sql`, `V2__stock_ledger_and_integrity.sql`). Never edit an applied migration; add a new version. Hibernate runs with `ddl-auto=validate`.

## Architecture and Setup

| Area | Current implementation |
| --- | --- |
| Runtime | Java 21, Spring Boot 3.3.4, Maven |
| Web/API | Spring MVC, Jakarta Bean Validation, SpringDoc OpenAPI 2.6.0 |
| Persistence | Spring Data JPA/Hibernate with PostgreSQL runtime driver; Flyway migrations; H2 test driver (tests run the same migrations) |
| Operations | Spring Boot Actuator health probes; `RequestIdFilter`; `Dockerfile` (non-root JRE image, health check) and `docker-compose.yml` (PostgreSQL + API); GitHub Actions CI (`mvn verify`, image build); `prod` profile (API docs off, graceful shutdown, forwarded headers) |
| Security | Spring Security, BCrypt, JJWT 0.12.6, stateless sessions with `Authorization: Bearer <token>`; `TenantFilter` after JWT authentication |
| Email | `spring-boot-starter-mail`, Gmail SMTP, async sending (`@EnableAsync`) |
| Local database | `jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:inventory_db}` |
| CORS | Configured by `app.cors.allowed-origins`; allows `Authorization` header |
| Seed data | The super admin is always ensured. With `SEED_DEMO_DATA=true` and no organizations, a `demo` organization is created with `admin@demo.com` / `Admin@123`, `staff@demo.com` / `Staff@123`, four categories and eight products |

### Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `FRONTEND_BASE_URL` | `http://localhost:5173` | Base for links in emails and `portalUrl` |
| `JWT_SECRET` | **none (required)** | Random signing key, at least 32 characters (`openssl rand -base64 48`) |
| `SUPER_ADMIN_EMAIL` / `SUPER_ADMIN_PASSWORD` / `SUPER_ADMIN_NAME` | `stockwise.rr.2026@gmail.com` / **none (required on first start)** / `StockWise Owner` | Seeded super admin, only when none exists yet; the password must satisfy PRD-SEC-02 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | Frontend origins allowed to call the API |
| `API_DOCS_ENABLED` | `true` | Set `false` in production to disable Swagger UI and `/v3/api-docs` |
| `MAIL_ENABLED` | `false` | Send real email (else log only) |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | `stockwise.rr.2026@gmail.com` / – | Gmail address and Google App Password (secret: environment or `application-local.properties` only) |
| `MAIL_FROM` | the Gmail address | From address |
| `PLATFORM_NOTIFICATION_EMAIL` | the Gmail address | Where StockWise receives subscription requests |
| `INVITATION_EXPIRY_DAYS` | `7` | Invitation validity |
| `SEED_DEMO_DATA` | `false` | Seed the demo organization |

### Commands

```powershell
# Configure PostgreSQL credentials/environment, then create inventory_db if needed
.\setup-database.ps1

# Run the automated tests (uses H2)
.\mvnw.cmd test

# Start the local API (uses PostgreSQL by default)
.\mvnw.cmd spring-boot:run
```

Do not commit real credentials. `.env.example` documents supported variables; application configuration uses Spring environment-variable placeholders.

### Client Request Contract (Header Bearer Tokens)

Clients authenticate API calls with `Authorization: Bearer <accessToken>`. Organization and super admin logins return:

```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "d8f7e3c1...",
  "token": "eyJhbGciOi...",
  "type": "Bearer",
  "id": 2,
  "name": "Fiona Admin",
  "email": "fiona@flowmart.com",
  "role": "ADMIN",
  "organizationSlug": "flow-mart",
  "organizationName": "Flow Mart"
}
```

`organizationSlug`/`organizationName` are `null` for the super admin.

## Verification Matrix

| Change type | Required verification |
| --- | --- |
| Any Java, configuration, schema, or dependency change | `.\mvnw.cmd test` and PRD review/change record |
| Authentication, authorization, tenant isolation, JWT, CORS, or public endpoint change | Automated tests plus manual/API-client checks for public, unauthenticated, `STAFF`, `ADMIN`, `SUPER_ADMIN` and cross-organization paths as applicable; confirm Header Bearer token authentication works as specified |
| Product, category, stock, organization, or repository logic change | Relevant service tests; include boundary coverage for validation, missing resources, per-organization uniqueness, and CSV parsing |
| Controller or response-contract change | Relevant MockMvc tests and update this API Contract |
| Entity or database schema change | New Flyway migration plus entity change; `.\mvnw.cmd test` (applies all migrations and validates the mappings); back up and test against a copy of PostgreSQL before production |
| Email change | Run with `MAIL_ENABLED=false` and check the logged email; spot-check with real Gmail SMTP |

## Change Record

| Date | Version | Change | Affected requirements | Verification |
| --- | --- | --- | --- | --- |
| 2026-09-28 | 1.0 | Created implementation baseline after repository review. | All baseline IDs | Repository inventory and source/config/test review completed; `.\mvnw.cmd test` passed: 31 tests, 0 failures, 0 errors, 0 skipped. |
| 2026-09-28 | 1.1 | Moved access and refresh tokens from JSON bodies to HttpOnly cookies; added cookie-based JWT authentication and CSRF protection. | PRD-AUTH-01, PRD-AUTH-02, PRD-SEC-01, PRD-SEC-02 | `.\mvnw.cmd test` passed: 32 tests. |
| 2026-09-28 | 1.2 | Changed authentication from cookies to Header Bearer tokens (`Authorization: Bearer <token>`); added Organization domain setup (Existing shift vs New Org) and CSV bulk import endpoint for team members, categories, products, and stock. | PRD-AUTH-01, PRD-AUTH-02, PRD-SEC-01, PRD-ORG-01, PRD-ORG-02 | `.\mvnw.cmd test` passed. |
| 2026-09-29 | 2.1 | Sessions no longer sign each other out (one refresh token per login, stored hashed; this caused users to be logged out after a reload once another login happened). Password policy for registration/invitations. Complete, user-readable error handling for all request failures. Security hardening: header-only tokens (cookie fallback removed), token no longer echoed in refresh errors, required random `JWT_SECRET` (committed sample key rejected), no committed password defaults, CSP/referrer/frame headers, CORS without credentials, CR/LF-safe email subjects, generic 500s with reference id. Existing databases: drop the unique constraint on `refresh_tokens.user_id` and clear the table. | PRD-AUTH-02, PRD-SEC-02 (new), PRD-SEC-03 (new), PRD-ERR-01 | `.\mvnw.cmd test` passed: 51 tests, 0 failures, 0 errors (new `SecurityAndErrorHandlingTest`, `PasswordPolicyTest`). Browser checks against the production frontend build: reload after access-token expiry keeps the session, error and password messages render. |
| 2026-09-29 | 2.0 | Multi-tenant StockWise: super admin platform, subscription requests, per-organization portals under `/api/orgs/{slug}`, tenant isolation, invitations, pending sign-ups approved by org admins, per-organization uniqueness, tenant-scoped CSV import (users invited, no default passwords), Gmail SMTP notifications. Old `/api/auth/register`, `/api/auth/login`, `/api/products`, `/api/categories`, `/api/users`, `/api/organization/*` routes removed. Requires a fresh database. | PRD-TEN-01, PRD-TEN-02, PRD-SUB-01, PRD-PLAT-01, PRD-PLAT-02, PRD-INV-01, PRD-MAIL-01 (new); PRD-AUTH-01, PRD-AUTH-02, PRD-SEC-01, PRD-USER-01, PRD-ORG-01, PRD-ORG-02, PRD-CAT-01, PRD-PROD-01, PRD-PROD-02, PRD-STOCK-01, PRD-STOCK-02 (changed) | `.\mvnw.cmd test` passed: 36 tests, 0 failures, 0 errors, including the end-to-end `MultiTenantFlowIntegrationTest` and cross-tenant/super-admin isolation tests in `ProductControllerTest`. |
| 2026-09-29 | 2.2 | Architecture review. Flyway migrations replace `ddl-auto=update` (existing databases baselined at V1). Stock integrity: row lock on stock in/out (no overselling under concurrency), optimistic locking on product edits (`409` on stale data), append-only stock ledger with history endpoint. Paged, sortable product list and server-side dashboard summary (previously every product was loaded). Category list counts products with one grouped query; removed the category-to-products cascade that could delete products with their category. Duplicates return `409`. Request id on every response, log line and error. Actuator health probes (mail excluded). `prod` profile, Dockerfile, docker-compose, CI workflow. `CurrentUser` centralises access to the authenticated user ahead of OIDC. | PRD-CAT-01, PRD-PROD-01, PRD-PROD-02, PRD-STOCK-01, PRD-OPS-01, PRD-OPS-02 (new), PRD-OPS-03 (new), PRD-ERR-01 | `.\mvnw.cmd test` passed: 59 tests, 0 failures, 0 errors (new `InventoryIntegrityTest`: ledger, stale edit, 12 concurrent stock-outs on 5 units, paging/sort validation, summary, request id, health). Local PostgreSQL migrated V1 to V2 with schema validation passing. Browser checks against the production frontend build passed. |
