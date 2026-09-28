# Inventory Management System Backend - Product Requirements Document

## Document Control

| Field | Value |
| --- | --- |
| Status | Living implementation baseline |
| Version | 1.1 |
| Last reviewed | 2026-09-28 |
| Product | Inventory Management System REST API |
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

The service is a Spring Boot REST backend for managing product inventory. It supports JWT-based authentication, two roles (`ADMIN` and `STAFF`), category and product management, stock adjustments, low-stock monitoring, and read-only user administration. PostgreSQL is the production persistence target; H2 is used by tests.

### Users and Roles

| Role | Intended capabilities |
| --- | --- |
| `ADMIN` | All authenticated reads, product/category CRUD, stock adjustments, and user listing |
| `STAFF` | Product/category reads and stock adjustments |

## Implemented Requirements

| ID | Requirement | Acceptance criteria |
| --- | --- | --- |
| PRD-AUTH-01 | Users can register and authenticate. | `POST /api/auth/register` creates a user; email is normalized to lowercase and must be unique. `POST /api/auth/login` validates credentials. Both set `access_token` and `refresh_token` cookies and return only non-sensitive user details. Omitted registration role defaults to `STAFF`. |
| PRD-AUTH-02 | The API maintains a JWT access-token and refresh-token lifecycle. | JWT access tokens contain the authenticated email subject and role/user claims. Access tokens default to 15 minutes; refresh tokens default to 7 days. A login or registration replaces that user's stored refresh token. `POST /api/auth/refresh` reads the refresh-token cookie and replaces both cookies; it rejects missing, revoked, expired, or unknown tokens. `POST /api/auth/logout` revokes the refresh token and clears both cookies. |
| PRD-SEC-01 | Protected API operations enforce role-based access. | Authentication uses the `HttpOnly` `access_token` cookie; the access and refresh tokens are never returned in JSON. Health, login, registration, CSRF initialization, OpenAPI, and Swagger UI are public. Product/category reads and stock adjustments allow `ADMIN` or `STAFF`; product/category writes and all user endpoints require `ADMIN`. |
| PRD-SEC-02 | Cookie-authenticated browser requests are protected against CSRF. | `GET /api/auth/csrf` initializes the readable `XSRF-TOKEN` anti-CSRF cookie. All state-changing requests except login and registration must include its value in the `X-XSRF-TOKEN` request header. |
| PRD-CAT-01 | Administrators manage product categories. | Authenticated users can list and retrieve categories. Administrators can create, update, and delete them. Category names are required, 2-100 characters, and unique. Deletion fails while products are assigned. |
| PRD-PROD-01 | Authorized users can retrieve inventory products. | `GET /api/products` returns products and accepts optional `search` (case-insensitive name or SKU match) and `categoryId` filters. `GET /api/products/{id}` returns one product. Missing resources return `404`. |
| PRD-PROD-02 | Administrators manage products. | Administrators can create, update, and delete products. Product name, SKU, non-negative price, non-negative quantity/minimum stock, and a valid category are required. SKUs are stored uppercase and unique. |
| PRD-STOCK-01 | Authorized users adjust stock safely. | `POST /api/products/{id}/stock/in` increases quantity by a positive integer. `POST /api/products/{id}/stock/out` decreases it only when sufficient inventory exists. Invalid quantities and insufficient stock return `400`. Request `notes` are accepted but are not persisted. |
| PRD-STOCK-02 | Users can monitor low stock. | `GET /api/products/low-stock` returns products whose `quantity <= minimumStock`. Product responses include the computed `lowStock` flag. |
| PRD-USER-01 | Administrators can inspect system users. | `GET /api/users` and `GET /api/users/{id}` return user ID, name, email, role, and creation timestamp without passwords. |
| PRD-OPS-01 | The service exposes operational and API documentation endpoints. | `GET /api/health` returns `UP`, service name, version, and a timestamp. Swagger UI is at `/swagger-ui.html`; OpenAPI JSON is at `/v3/api-docs`. |
| PRD-ERR-01 | Client-facing failures use a consistent JSON error shape. | Not found, business-rule, token, credential, access, and validation failures expose status, error, message, path, timestamp, and validation field errors where applicable. |

## API Contract and Authorization

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/api/health` | Public | Health status |
| GET | `/api/auth/csrf` | Public | Initialize the CSRF cookie for browser requests |
| POST | `/api/auth/register` | Public | Register and set HttpOnly session cookies |
| POST | `/api/auth/login` | Public | Authenticate and set HttpOnly session cookies |
| POST | `/api/auth/refresh` | Public + CSRF | Refresh the cookie session using the refresh cookie |
| POST | `/api/auth/logout` | Public + CSRF | Revoke refresh token and clear session cookies |
| GET | `/api/categories`, `/api/categories/{id}` | ADMIN, STAFF | Read categories |
| POST, PUT, DELETE | `/api/categories`, `/api/categories/{id}` | ADMIN | Manage categories |
| GET | `/api/products`, `/api/products/{id}`, `/api/products/low-stock` | ADMIN, STAFF | Read inventory and alerts |
| POST, PUT, DELETE | `/api/products`, `/api/products/{id}` | ADMIN | Manage products |
| POST | `/api/products/{id}/stock/in`, `/api/products/{id}/stock/out` | ADMIN, STAFF | Adjust stock |
| GET | `/api/users`, `/api/users/{id}` | ADMIN | Read users |

## Data Model

| Entity/table | Key fields and rules |
| --- | --- |
| `users` | `id`, name, unique email, BCrypt password, `ADMIN`/`STAFF` role, created timestamp |
| `categories` | `id`, unique name, optional description; a category owns zero or more products |
| `products` | `id`, name, description, unique SKU, decimal price (12,2), quantity, minimum stock, nullable category relation, created/updated timestamps |
| `refresh_tokens` | One token row per user, unique token, expiry timestamp, revoked flag, created timestamp; deletion cascades with its user |

`schema.sql` supplies the PostgreSQL schema and indexes. Hibernate is configured with `ddl-auto=update`, so entity/schema changes must be assessed together and tested against PostgreSQL where practical.

## Architecture and Setup

| Area | Current implementation |
| --- | --- |
| Runtime | Java 21, Spring Boot 3.3.4, Maven |
| Web/API | Spring MVC, Jakarta Bean Validation, SpringDoc OpenAPI 2.6.0 |
| Persistence | Spring Data JPA/Hibernate with PostgreSQL runtime driver; H2 test driver |
| Security | Spring Security, BCrypt, JJWT 0.12.6, stateless sessions |
| Local database | `jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:inventory_db}` |
| CORS | Configured by `app.cors.allowed-origins`; default local frontend origins only |
| Cookies | `access_token` is `HttpOnly`, `SameSite=Lax`, path `/`; `refresh_token` is `HttpOnly`, `SameSite=Lax`, path `/api/auth`. `COOKIE_SECURE` controls the Secure attribute and must be `true` in HTTPS environments. |
| Seed data | An empty user table triggers two default accounts, four categories, and eight products |

### Commands

```powershell
# Configure PostgreSQL credentials/environment, then create inventory_db if needed
.\setup-database.ps1

# Run the automated tests (uses H2)
.\mvnw.cmd test

# Start the local API (uses PostgreSQL by default)
.\mvnw.cmd spring-boot:run
```

Do not commit real credentials. `.env.example` documents supported database, JWT, and port variables; application configuration uses Spring environment-variable placeholders.

### Browser Client Contract

Browser clients must use `credentials: 'include'` for API calls. After login or registration, call `GET /api/auth/csrf`, read the non-HttpOnly `XSRF-TOKEN` cookie, and send its value as `X-XSRF-TOKEN` on every state-changing request (including refresh and logout). JavaScript cannot read the access or refresh token cookies.

## Verification Matrix

| Change type | Required verification |
| --- | --- |
| Any Java, configuration, schema, or dependency change | `.\mvnw.cmd test` and PRD review/change record |
| Authentication, authorization, JWT, cookies, CSRF, CORS, or public endpoint change | Automated tests plus manual/API-client checks for public, unauthenticated, `STAFF`, and `ADMIN` paths as applicable; confirm tokens never appear in a JSON body and production cookies are Secure |
| Product, category, stock, or repository logic change | Relevant service tests; include boundary coverage for validation, missing resources, uniqueness, and insufficient stock where affected |
| Controller or response-contract change | Relevant MockMvc tests and update this API Contract plus `Inventory-Management-System.postman_collection.json` when a request/response or endpoint changes |
| Entity or database schema change | Automated tests, review `schema.sql` and JPA mappings together, and validate migration/startup with PostgreSQL before release |
| Build/runtime/configuration change | Automated tests and verify documented commands and environment variables remain accurate |

Current automated coverage includes context startup, auth service/controller behavior, product service/controller behavior, and category service behavior. Refresh-token persistence edge cases, user endpoints, health, CORS, and PostgreSQL integration are not directly covered by dedicated tests at this baseline.

## Repository Baseline Reviewed

All 59 tracked project files were reviewed on 2026-09-28 before this PRD was added. The operational source is organized as follows:

| Location | Contents |
| --- | --- |
| Root | Maven project descriptor/wrapper, README, environment template, database setup script, Postman collection, and Git ignore rules |
| `src/main/java/com/example/inventory` | Application entry point; configuration, controllers, DTOs, JPA entities, repositories, security, services, and exception handling |
| `src/main/resources` | Application properties and PostgreSQL schema |
| `src/test/java` | Spring context, MockMvc controller, and Mockito service tests |
| `src/test/resources` | H2 test profile properties |

Generated build output under `target/` and local Git metadata were intentionally excluded from the product baseline. An untracked `.github/modernize` helper directory is also outside the tracked application baseline.

## Change Record

| Date | Version | Change | Affected requirements | Verification |
| --- | --- | --- | --- | --- |
| 2026-09-28 | 1.0 | Created implementation baseline after repository review. | All baseline IDs | Repository inventory and source/config/test review completed; `.\mvnw.cmd test` passed: 31 tests, 0 failures, 0 errors, 0 skipped. |
| 2026-09-28 | 1.1 | Moved access and refresh tokens from JSON bodies to HttpOnly cookies; added cookie-based JWT authentication and CSRF protection. | PRD-AUTH-01, PRD-AUTH-02, PRD-SEC-01, PRD-SEC-02 | `.\mvnw.cmd test` passed: 32 tests, 0 failures, 0 errors, 0 skipped, including access-cookie authentication. Postman collection JSON validated with no legacy bearer/token-body transport. |
