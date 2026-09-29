# StockWise Frontend - Integration PRD

## Purpose

This document is the implementation contract for the separate frontend repository that consumes the StockWise backend (see `PRD.md` v2.0). It defines the three frontend areas, their routes, the backend endpoints each one calls, and the authentication rules.

## Product Goal

StockWise serves many client organizations. The frontend has three separate areas:

1. **Public pages**: the subscription request page (for organizations that want StockWise) and the invitation acceptance page.
2. **Super admin portal** (`/platform`): used only by the StockWise owner. No one else can log in there.
3. **Organization portals** (`/o/{slug}`): one per client organization, created by the super admin. Only that organization's members can sign in and see its data.

## Backend Connection

| Setting | Local value |
| --- | --- |
| API base URL | `http://localhost:8080` (use `VITE_API_BASE_URL`, never hard-code) |
| Health endpoint | `GET /api/health` |
| OpenAPI / Swagger UI | `GET /v3/api-docs`, `http://localhost:8080/swagger-ui.html` |
| Allowed local frontend origins | `http://localhost:5173`, `http://localhost:3000`, `http://127.0.0.1:5173` |
| Frontend base URL in emails | Backend `FRONTEND_BASE_URL` (default `http://localhost:5173`) |

The backend emails links to these exact frontend routes, so they must exist:

| Link in email | Frontend route |
| --- | --- |
| Invitation | `/invite/{token}` |
| Organization login | `/o/{slug}/login` |
| Pending users (to org admins) | `/o/{slug}/users?status=PENDING` |
| Subscription requests (to StockWise) | `/platform/requests` |

## Route Map

| Route | Area | Auth | Purpose |
| --- | --- | --- | --- |
| `/` | Public | – | Marketing landing with a "Request StockWise" call-to-action |
| `/request-access` | Public | – | Subscription request form |
| `/invite/:token` | Public | – | Accept invitation: set name and password |
| `/platform/login` | Super admin | – | Super admin login |
| `/platform/requests` | Super admin | SUPER_ADMIN | Subscription requests: approve (create org) / reject |
| `/platform/organizations` | Super admin | SUPER_ADMIN | Organizations list, create organization |
| `/platform/organizations/:id` | Super admin | SUPER_ADMIN | Detail: portal URL, status toggle, admins, invite admin |
| `/o/:slug/login` | Org portal | – | Organization login |
| `/o/:slug/register` | Org portal | – | Self-registration (pending approval) |
| `/o/:slug/setup` | Org portal | ADMIN | First-login setup: new vs existing data, CSV upload |
| `/o/:slug` (dashboard) | Org portal | ADMIN, STAFF | Inventory summary and low-stock alerts |
| `/o/:slug/products`, `/o/:slug/categories` | Org portal | ADMIN, STAFF | Inventory (writes ADMIN only) |
| `/o/:slug/users` | Org portal | ADMIN | Members: approve/reject sign-ups, invite, disable/enable |
| `/o/:slug/import` | Org portal | ADMIN | CSV import (also reachable after setup) |

Keep the super admin session and the organization session separate. A token issued for one area must never be used in the other. Route guards: `/platform/**` requires `role === "SUPER_ADMIN"`. `/o/:slug/**` requires `organizationSlug === :slug`; otherwise redirect to `/o/:slug/login`.

## Authentication Contract (Header Bearer Tokens)

Every protected request sends `Authorization: Bearer <accessToken>`.

### Login responses

`POST /api/orgs/{slug}/auth/login` and `POST /api/platform/auth/login` both return:

```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "d8f7e3c1...",
  "type": "Bearer",
  "id": 2,
  "name": "Fiona Admin",
  "email": "fiona@flowmart.com",
  "role": "ADMIN",
  "organizationSlug": "flow-mart",
  "organizationName": "Flow Mart"
}
```

For the super admin, `role` is `"SUPER_ADMIN"` and the organization fields are `null`.

### Login errors to handle

| Status | Meaning | UI |
| --- | --- | --- |
| `401` | Wrong email/password, or the user is not a member of this organization | "Invalid email or password" |
| `403` | Correct password, but the account is `PENDING` / `REJECTED` / `DISABLED`, or the organization is suspended | Show the backend `message`, e.g. "Your account is awaiting approval by your organization administrator." |
| `404` | Unknown organization slug | "Organization not found" page |

### Session & refresh flow

1. After login, store the session per area (`stockwise_platform_session`, `stockwise_org_session`) so a page reload keeps the user signed in. Each login is its own server-side session, so signing in elsewhere never signs this browser out.
2. On `401` from a protected call, call `POST /api/auth/refresh` with `{ refreshToken }` and retry once. Concurrent `401`s must share a single refresh call.
3. Tokens go only in the `Authorization` header. The backend ignores cookies.
4. If refresh fails (the user was disabled, the organization was suspended, or the token expired), clear the session and redirect to the area's login.
5. Logout: `POST /api/auth/logout` with `{ refreshToken }`.

### Password rules

Registration and invitation acceptance enforce 8-64 characters with an uppercase letter, a lowercase letter, a number and a special character, and no spaces. Show a live checklist and block submission until every rule passes. The server repeats the check and returns `validationErrors.password` listing the unmet rules.

## Public Pages

### Subscription request (`/request-access`)

`POST /api/public/subscription-requests`

```json
{
  "organizationName": "Flow Mart",
  "contactName": "Fiona",
  "contactEmail": "fiona@flowmart.com",
  "contactPhone": "+91 98xxxxxx",
  "hasExistingData": true,
  "message": "We have 2 stores and ~3,000 SKUs"
}
```

Returns `201 { message }`. Show a thank-you state. StockWise and the requester both receive an email.

### Accept invitation (`/invite/:token`)

1. `GET /api/public/invitations/{token}` returns `{ email, name, role, organizationName, organizationSlug, expiresAt }`. On `400`/`404`, show "This invitation has expired or was already used".
2. Show the form (email read-only, name prefilled, password of at least 8 characters with confirmation). Submit `POST /api/public/invitations/{token}/accept` with `{ name, password }`.
3. On success, redirect to `/o/{organizationSlug}/login`.

## Super Admin Portal (`/platform`)

| Screen | Endpoints |
| --- | --- |
| Login | `POST /api/platform/auth/login` `{ email, password }` |
| Requests list (tabs Pending / Approved / Rejected) | `GET /api/platform/subscription-requests?status=PENDING` |
| Approve request → "Create organization" dialog, prefilled from the request (name, contact email, admin email = contact email) | `POST /api/platform/organizations` `{ name, slug, description?, contactEmail?, adminName?, adminEmail?, subscriptionRequestId }` |
| Reject request (optional note) | `POST /api/platform/subscription-requests/{id}/reject` `{ note? }` |
| Organizations list / create without a request | `GET` / `POST /api/platform/organizations` |
| Organization detail | `GET /api/platform/organizations/{id}` (shows `portalUrl` to copy and send to the client) |
| Suspend / reactivate | `PATCH /api/platform/organizations/{id}/status` `{ status: "SUSPENDED" \| "ACTIVE" }` |
| Admins and invite admin | `GET /api/platform/organizations/{id}/admins`, `POST /api/platform/organizations/{id}/admin-invitations` `{ name?, email }` |

Slug rules: 3-63 characters, lowercase letters, digits and hyphens, and it cannot start or end with a hyphen. Reserved slugs: `api, platform, admin, invite, register, request, login, www, app, stockwise`. Suggest a slug from the organization name, for example "Flow Mart" → `flow-mart`. The super admin portal never shows an organization's products or other inventory data.

## Organization Portal (`/o/:slug`)

On entry, call `GET /api/public/organizations/{slug}` and show the organization name on the login and register pages. On `404`, show "Organization not found".

### Register (`/o/:slug/register`)

`POST /api/orgs/{slug}/auth/register` `{ name, email, password }` (password of at least 8 characters). This returns `202 { message, email, status: "PENDING" }`. Show "Your request was sent to your organization admin. You'll get an email when approved." Do not log the user in.

### First-login setup (`/o/:slug/setup`, ADMIN)

After an admin logs in, call `GET /api/orgs/{slug}/organization`. If `setupCompleted === false`, send them to setup:

- **New organization, no existing data**: `POST /api/orgs/{slug}/organization/setup` `{ "hasExistingData": false }`, then go to the dashboard to create categories and products.
- **Existing data**: `POST .../organization/setup` `{ "hasExistingData": true }`, then upload a CSV with `POST /api/orgs/{slug}/organization/import-csv` (`multipart/form-data`, field `file`, max 10MB). Show the counts and `warningsOrSkipped` from the response.

### CSV format

Header names are case- and punctuation-insensitive. `Type` selects the row kind. When it is missing, the kind is inferred from the columns.

```csv
Type,Name,Email,Role,SKU,Price,Quantity,MinimumStock,CategoryName,Description
USER,Jane Staff,jane@org.com,STAFF,,,,,,
CATEGORY,Electronics,,,,,,,,Gadgets and appliances
PRODUCT,Wireless Mouse,,,MOUSE-001,29.99,50,10,Electronics,Ergonomic wireless mouse
```

- `USER` rows are **emailed an invitation** to set their own password. No passwords are imported. `Role` is `ADMIN` or `STAFF` (default `STAFF`).
- `PRODUCT` rows require `Name`, `SKU` and `Price`. An unknown `CategoryName` is created automatically.
- Existing emails, category names and SKUs are skipped with a warning.

Response: `{ importedUsersCount, importedCategoriesCount, importedProductsCount, message, warningsOrSkipped[] }`.

### Inventory

| Screen | Endpoints |
| --- | --- |
| Dashboard | `GET .../products/summary`, `GET .../products/low-stock`, `GET .../organization` |
| Products | `GET .../products?search=&categoryId=&page=&size=&sort=` (paged), `GET/POST/PUT/DELETE .../products[/{id}]` (writes ADMIN) |
| Stock | `POST .../products/{id}/stock/in` and `/stock/out` `{ quantity, notes? }`; history `GET .../products/{id}/movements?page=&size=` |
| Categories | `GET/POST/PUT/DELETE .../categories[/{id}]` (writes ADMIN) |

### Members (`/o/:slug/users`, ADMIN)

| Action | Endpoint |
| --- | --- |
| List with status tabs (Pending first, with a badge count) | `GET /api/orgs/{slug}/users?status=PENDING` (omit `status` for all) |
| Approve / reject a sign-up | `POST .../users/{id}/approve`, `POST .../users/{id}/reject` |
| Disable / enable | `POST .../users/{id}/disable`, `POST .../users/{id}/enable` |
| Invite member | `POST .../users/invite` `{ name?, email, role? }` |

`UserResponse`: `{ id, name, email, role, status, createdAt }`.

### Paging, totals and stock history

- `GET .../products` returns `{ content: ProductResponse[], page, size, totalElements, totalPages }`. `page` starts at 0; `size` defaults to 20 (max 100). `sort` is one of `name`, `sku`, `price`, `quantity`, `createdAt`, `updatedAt`, optionally followed by `,asc` or `,desc`. Reset to page 0 when a filter changes.
- `GET .../products/summary` returns `{ totalProducts, totalUnits, inventoryValue, lowStockCount }`. Use it for dashboard figures instead of loading all products.
- `GET .../products/{id}/movements` returns the same page shape with `{ id, type, quantityChange, quantityAfter, notes, performedBy, createdAt }`, newest first. `type` is `INITIAL`, `STOCK_IN`, `STOCK_OUT`, `ADJUSTMENT` or `IMPORT`; `quantityChange` is negative for stock removed.
- `ProductResponse` includes `version`. Send it back with `PUT .../products/{id}`: if someone changed the product meanwhile, the server answers `409` ("This product was changed by someone else…") and saves nothing. Offer to reload the latest version. Change stock only through stock in/out so it is recorded in the history.

## Roles and UI Access

| Capability | SUPER_ADMIN | ADMIN | STAFF |
| --- | --- | --- | --- |
| Review subscription requests, create/suspend organizations, invite org admins | Yes | No | No |
| Organization setup & CSV import | No | Yes | No |
| View products, categories, low stock | No | Yes | Yes |
| Adjust stock in/out | No | Yes | Yes |
| Create/edit/delete products and categories | No | Yes | No |
| Approve sign-ups, invite/disable members | No | Yes | No |

## Error Handling

```ts
type ApiError = {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  requestId: string; // also in the X-Request-Id response header; include it when reporting a problem
  validationErrors?: Record<string, string>;
};
```

Messages are written for end users. Display them as-is:

- Show a short heading based on `status` (0: can't reach server, 400: check your input, 401: sign-in required, 403: not allowed, 404: not found, 409: conflict, 413: file too large, 5xx: server error), followed by `message`.
- Show each `validationErrors` entry next to its input. List any field without an input on screen in the banner, so no reported problem is hidden.
- `5xx` messages contain a support reference (the `requestId`). Never show raw exception text.
- `409` means a duplicate (name, SKU, email) or a stale edit; show the message and, for stale edits, a way to reload.
- A `403` with "you are not a member of this organization" means the stored session belongs to another organization: clear it and redirect to `/o/:slug/login`.

## Frontend Completion Checklist

- [ ] Separate route areas: public, `/platform`, `/o/:slug`, with guards on role and `organizationSlug`.
- [ ] `Authorization: Bearer <accessToken>` on all protected requests; refresh-on-401 flow.
- [ ] Subscription request page.
- [ ] Invitation acceptance page `/invite/:token`.
- [ ] Super admin: login, requests (approve by creating the organization, or reject), organizations (create, detail, suspend/activate, invite admin).
- [ ] Organization login and register (pending message) using the slug from the URL.
- [ ] Login error handling for `401` / `403` (pending, rejected, disabled, suspended) / `404`.
- [ ] First-login setup: new vs. existing data, CSV upload with result summary.
- [ ] Inventory screens scoped to `/api/orgs/{slug}`.
- [ ] Members screen with the pending-approval queue.
