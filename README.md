# Todo List

A production-ready full-stack application built with a Spring Boot 3.5.6 REST API (Java 17) and a React frontend, featuring JWT-based authentication, role-based access control, per-user data isolation, account lockout, token revocation, and IP-based rate limiting.

## Storage Note

The application supports three runtime profiles:

- **default**: in-memory H2 database with Flyway-managed schema validation. Data is lost when the process restarts.
- **dev**: in-memory H2 with the schema re-created on every start and the H2 web console enabled. Intended for local development.
- **docker**: persistent PostgreSQL database, used by the Docker Compose setup.

Use the **docker** profile for any environment that requires persistent storage.

## Tech Stack

- **Framework**: Spring Boot 3.5.6
- **Language**: Java 17 (LTS)
- **Security**: Spring Security 6.x, JWT (jjwt 0.12.6), BCrypt
- **Database**: H2 (in-memory) / PostgreSQL, JPA/Hibernate, Flyway
- **Documentation**: SpringDoc OpenAPI, Swagger UI
- **Tools**: Lombok, Jackson, Maven
- **Testing**: JUnit 5.11.3, Mockito, RestAssured 5.5.0, AssertJ 3.26.3, Spring Boot Test

## Core Features

### Authentication and Security

- Registration and login endpoints protected by stateless JWT authentication.
- Token expiration defaults to 24 hours and is configurable.
- HS256 signing with a 256-bit secret key; startup fails fast if `JWT_SECRET` is missing or too short.
- Password policy enforced at registration: 8-100 characters, at least one lowercase letter, one uppercase letter, and one digit. The same policy is enforced for password changes (`PUT /api/users/me/password`). Both rules live as Bean Validation annotations on `UserRegisterRequest` and `PasswordChangeRequest` respectively — if the policy ever changes, both DTOs must be updated together.
- Account lockout after repeated failed logins (5 attempts by default, configurable). While locked, login returns `423 Locked`.
- Every token carries a unique `jti` claim. `POST /api/auth/logout` blacklists the presented token, so a logged-out token is rejected on subsequent requests.
- IP-based rate limiting applied to all `/api/auth/**` endpoints. Exceeding the budget returns `429 Too Many Requests`.
- Optional bootstrap of the first `ADMIN` account at startup via environment variables.

### Role-Based Access Control

- Two roles: `USER` and `ADMIN`.
- `/api/tasks/**` requires authentication and enforces per-user data isolation: each caller only sees and manages their own tasks.
- `/api/admin/**` requires the `ADMIN` role and exposes user management and cross-user task visibility.
- `/api/users/me**` (profile: view email/role, change email, change password) requires authentication for either role; the caller always operates on their own account.
- Missing or invalid credentials return `401 Unauthorized` (JSON); authenticated callers without sufficient rights receive `403 Forbidden`.

### Admin Console

Explicit endpoints under `/api/admin/**`, gated by the `ADMIN` role:

- List all users (paginated).
- Change a user's role between `USER` and `ADMIN`.
- Delete a user; the user's tasks are removed as well.
- List tasks across all users (paginated).

Admin operations are self-protected: an administrator cannot change their own role or delete their own account (`400 Bad Request`).

The frontend ships a full Admin Panel UI for these endpoints (see [Admin Panel](#admin-panel-frontend-ui)).

### Task Management

- Full CRUD operations for todo items (create returns `201 Created`, delete returns `204 No Content`).
- `GET /api/tasks` accepts optional `status` (`TODO`, `IN_PROGRESS`, or `DONE`), `priority` (`LOW`, `MEDIUM`, or `HIGH`), and `overdue` (`true`) query parameters, usable independently or combined. Omitting them returns all of the caller's tasks. An unrecognized value for `status`/`priority` returns `400 Bad Request`.
- `?overdue=true` returns only the caller's overdue tasks, using **the same rule as the response field**: past due (relative to the server's current date) and not `DONE`. `?overdue=false` or omitting the parameter applies no filter.
- `?dueBefore=YYYY-MM-DD` and `?dueAfter=YYYY-MM-DD` filter by the task's `dueDate`, independently or combined. Both bounds are **inclusive**: `dueBefore=X` means `dueDate <= X` ("on or before X"), `dueAfter=X` means `dueDate >= X` ("on or after X"). Setting both pins a range — `dueBefore=X&dueAfter=X` returns only tasks due **exactly** on `X`. Tasks with no `dueDate` are excluded whenever any date filter is active. An inverted/empty range (e.g. `dueAfter` later than `dueBefore`) returns an empty page, not an error. Dates use ISO `yyyy-MM-dd`; a malformed date returns `400 Bad Request`. Date filters compose with `status`, `priority`, and `overdue`.
- `?sortBy=<field>` and `?direction=<asc|desc>` sort the result. `sortBy` accepts `createdAt`, `dueDate`, `priority`, or `title` (default `createdAt`); `direction` accepts `asc` or `desc`, case-insensitive (default `desc`). An invalid `sortBy` or `direction` returns `400 Bad Request`. Sorting composes with all filters and pagination; the single sort field maps to a whitelisted entity property, so no arbitrary property injection is possible. Tasks with no `dueDate` use the database's default NULL placement when sorting by `dueDate` (acceptable for this feature).
- Per-user data isolation via the `user_id` foreign key.
- Partial updates via `PATCH`; explicitly passing `null` clears `description` and `dueDate`.
- Input validation with Bean Validation annotations.
- Every `TaskResponse` includes an `overdue` boolean. A task is overdue iff it has a `dueDate` **before** the server's current date **and** its status is not `DONE` (a task due today is not overdue, and a completed task is never overdue). Computed server-side in the service layer — pure mapping, no extra query.
- Every `TaskResponse` also includes an `ownerUsername` string (the username of the task's owner, populated from the already-loaded `user` entity graph). This lets admin listings show the owner without a client-side lookup or an extra query.
- Pagination with `?page` (0-based) and `?size` (clamped to 1–100) parameters; results are sorted by `createdAt` descending.
- Filtering is built on JPA Specifications: `TaskRepository` extends `JpaSpecificationExecutor<Task>` and overrides `findAll(Specification<Task>, Pageable)` with an `@EntityGraph(attributePaths = "user")` join so each filter predicate (ownership + optional status/priority/overdue/due-date bounds) stays a single query with no N+1. The spec builder takes a nullable owner: a non-null owner scopes the query to that user (user path), while a `null` owner omits the ownership predicate entirely and matches every user's tasks (admin cross-user path). The unfiltered user path keeps using `findByUser(...)` with its own entity graph.

### Architecture

- Clean separation of concerns: Controller, Service, Repository layers.
- DTO pattern for all request and response objects.
- Global exception handling via `@RestControllerAdvice` with centralized JSON error responses and appropriate HTTP status codes.
- Database schema managed by Flyway migrations.

## How to Run

### Prerequisites

- Java 17 or later.
- Maven 3.8 or later, or the bundled Maven wrapper (`./mvnw`).

### Build and Run

```bash
# Clean and build the project
./mvnw clean install

# Run with the default profile (in-memory H2; JWT_SECRET is required)
JWT_SECRET=<your-256-bit-secret> ./mvnw spring-boot:run

# Run with the dev profile (in-memory H2, H2 console, schema re-created)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The application starts on `http://localhost:8080` by default.

### Run with Docker Compose

The repository includes a `Dockerfile` for the backend, a Dockerfile for the React frontend under `frontend/`, and a `docker-compose.yml` that wires together PostgreSQL, the backend, and the frontend.

```bash
# 1. Create the environment file from the template
cp .env.example .env

# 2. Generate a real secret and set it in .env
openssl rand -base64 32

# 3. Start the stack
docker compose up --build
```

The backend listens on `http://localhost:8080`, the frontend on `http://localhost:3000`. To bootstrap the first admin account, set `ADMIN_USERNAME`, `ADMIN_PASSWORD`, and `ADMIN_EMAIL` in `.env` before the first start.

## Admin Panel (Frontend UI)

The React frontend (`frontend/`) includes an Admin Panel that surfaces the `/api/admin/**` endpoints in the browser. It is reachable only by `ADMIN`-role accounts.

### Visibility

- The top bar shows a **Tasks / Profile / Admin** switcher. The **Admin** tab is rendered **only** when the JWT `role` claim is `ADMIN`; regular `USER` accounts never see it. **Tasks** and **Profile** are available to every authenticated user.
- The Admin Panel itself performs a second client-side guard: if the decoded role is not `ADMIN`, it renders an *Access denied* message instead of the panel. The backend independently enforces `403 Forbidden` for any call made without the `ADMIN` role.

### Profile Panel

The **Profile** tab (visible to all authenticated users) surfaces the self-service endpoints in the browser:

- Shows the caller's username, role, email, and member-since date.
- An email change form (calls `PATCH /api/users/me`; errors such as a duplicate email or an invalid address are shown in the error banner).
- A password change form with current/new/confirm fields (client-side new==confirm check; `PUT /api/users/me/password`). Success messages and API errors are shown in banners. Username is read-only — there is no way to change it.

### Capabilities

- **Users tab** — paginated list of every account (id, username, email, role, created-at). For each user (except yourself) you can:
  - Switch their role between `USER` and `ADMIN` via a dropdown. Role changes persist immediately.
  - Delete the user with a confirmation prompt (this cascades to all of their tasks). If you are on the last page and delete the final row, the panel steps back a page.
- **Tasks tab** — paginated list of every task across all users (title, status, priority, owner, due date, created-at). Each row's owner is the server-supplied `ownerUsername` field — no client-side user lookup needed. The tab mirrors the same filters as the regular task board (status, priority, overdue, due-date range) plus sorting, applied server-side across all users via `GET /api/admin/tasks`.
- Errors (e.g. "Cannot change your own role via this endpoint", "Cannot delete your own account via this endpoint", or "Cannot demote or delete the last remaining ADMIN") are surfaced in an error banner. The role dropdown and delete button are disabled on your own row, mirroring the backend's self-protection rules.

### Bootstrap the first admin

The first `ADMIN` account is created at startup by `BootstrapAdminRunner` when `ADMIN_PASSWORD` is set:

| Variable | Description | Default |
|----------|-------------|---------|
| `ADMIN_USERNAME` | Username for the bootstrap admin account | `admin` |
| `ADMIN_PASSWORD` | Password for the bootstrap admin account; blank disables bootstrap | *(empty)* |
| `ADMIN_EMAIL` | Email for the bootstrap admin account | `admin@example.com` |

For Docker Compose, put these in `.env` before the first `docker compose up --build`. For a plain local run, export them before starting the backend, e.g.:

```bash
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD='Str0ng-Admin-Pass!'
export ADMIN_EMAIL=admin@example.com
./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
$env:ADMIN_USERNAME="admin"
$env:ADMIN_PASSWORD="Str0ng-Admin-Pass!"
$env:ADMIN_EMAIL="admin@example.com"
.\mvnw.cmd spring-boot:run
```

If no `ADMIN_PASSWORD` is provided, no admin is bootstrapped; promote an existing user through the H2 console (dev profile) or any other admin-created account.

## Security and JWT Usage

### Token Configuration

- **Default expiration**: 24 hours (86,400,000 ms).
- **Secret**: Base64-encoded value that decodes to at least 256 bits (32 bytes). Set via the `JWT_SECRET` environment variable; there is no default and startup aborts without it.
- **Algorithm**: HS256.

### Authentication Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register a new `USER` account (`201 Created`) |
| POST | `/api/auth/login` | Authenticate and obtain a JWT |
| POST | `/api/auth/logout` | Revoke the presented token (`204 No Content`) |

### Admin Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/users` | List all users, paginated (`?page=0&size=20`) |
| PATCH | `/api/admin/users/{id}/role` | Change a user's role; body `{"role": "USER" or "ADMIN"}` |
| DELETE | `/api/admin/users/{id}` | Delete a user and their tasks (`204 No Content`) |
| GET | `/api/admin/tasks` | List all tasks across users, paginated. Supports the same optional filters/sort as `GET /api/tasks` — `status`, `priority`, `overdue`, `dueBefore`, `dueAfter`, `sortBy`, `direction` — plus `?page` / `?size`. No owner scoping: results span every user, and each `TaskResponse` carries `ownerUsername`. Invalid filter values return `400 Bad Request`. Example: `GET /api/admin/tasks?status=DONE&sortBy=title&direction=asc&page=0&size=50` |

### Profile Endpoints

Self-service account management for the authenticated caller (either role). The caller is always the logged-in user — there is no way to target another account here (use `/api/admin/**` for that).

| Method | Endpoint | Body | Description |
|--------|----------|------|-------------|
| GET | `/api/users/me` | — | Return the caller's profile (`200 UserResponse` with `id, username, email, role, createdAt`) |
| PATCH | `/api/users/me` | `{"email": "..."}` | Change the caller's email (`200 UserResponse`). If the new email equals the current one it is a no-op success. A different email already in use returns `409 Conflict`; an invalid/blank email returns `400 Bad Request`. Username is immutable and is **not** exposed here. |
| PUT | `/api/users/me/password` | `{"currentPassword": "...", "newPassword": "..."}` | Change the caller's password (`204 No Content`). The current password is verified first; a mismatch returns `400 Bad Request` with `{"error":"Current password is incorrect"}`. `newPassword` enforces the same policy as registration (8–100 characters with ≥1 lowercase, ≥1 uppercase, ≥1 digit); a violation returns `400 Bad Request`. |

**Known limitations (intentional, not implemented):**

- Changing a password does **not** revoke the caller's other active tokens. The API is stateless JWT; tokens remain valid until they expire or are explicitly revoked via logout (`POST /api/auth/logout`).
- `PUT /api/users/me/password` is **not** rate-limited or lockout-protected. The rate limiter only covers `/api/auth/**`. This is a future hardening item (the endpoint requires the current password, which raises the bar, but brute-forcing it is still possible).

**Profile endpoints curl examples:**

```bash
# View your own profile
curl -X GET http://localhost:8080/api/users/me \
  -H "Authorization: Bearer <your_jwt_token>"

# Change your email
curl -X PATCH http://localhost:8080/api/users/me \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_jwt_token>" \
  -d '{"email":"new.address@example.com"}'

# Change your password (verifies the current one first)
curl -X PUT http://localhost:8080/api/users/me/password \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_jwt_token>" \
  -d '{"currentPassword":"OldPass123!","newPassword":"NewPass456!"}'
```

### Secured Endpoints

All endpoints under `/api/**` require a valid JWT, with the following exceptions:

- `/api/auth/login` and `/api/auth/register` are public.
- `/api/auth/logout` requires authentication.
- `/api/test/health` and `/` are public.

**Bearer token format:**

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIiwiaWF0IjoxNzQ0...
```

**Role mapping:**

- `ROLE_USER`: standard user operations on own tasks plus self-service profile management (`/api/users/me**`).
- `ROLE_ADMIN`: all user operations plus admin endpoints (all tasks, user management).

### Account Lockout

After the configured number of consecutive failed login attempts (default 5), the account is locked for the configured duration (default 15 minutes). While locked, login attempts return `423 Locked`. A successful login clears the failed-attempt counter and any active lock.

### Rate Limiting

The authentication endpoints are throttled per client IP using a fixed-window counter. When the configured request budget is exceeded, requests return `429 Too Many Requests`. The rate limiter is in-memory and per-instance; a shared store is required for multi-instance deployments.

### Observability (Spring Boot Actuator)

Spring Boot Actuator is enabled under the `/actuator` base path. Only the following endpoints are exposed: `health`, `info`, `metrics`, and `prometheus`.

| Endpoint | Access | Description |
|---|---|---|
| `GET /actuator/health` | Public | Liveness/readiness. Shows full component details only to `ADMIN` users. |
| `GET /actuator/info` | Public | Static application metadata (`app.name`, `app.version`, `app.description`) plus JVM/OS info. |
| `GET /actuator/metrics` | `ADMIN` only | Lists available metric names; per-metric data at `/actuator/metrics/{name}`. |
| `GET /actuator/prometheus` | `ADMIN` only | Metrics in Prometheus text exposition format for scraping. |

Security model:

- `health` and `info` are public (no authentication required).
- `metrics` and `prometheus` require the `ADMIN` role (`/actuator/**` is ADMIN-only in the security configuration).
- Health component details are gated with `show-details: when-authorized` plus `roles: ADMIN` — an anonymous or `USER`-role caller sees only the aggregate `status`, while an `ADMIN` caller sees the `components` map.
- `management.prometheus.metrics.export.enabled` is set explicitly so the Prometheus registry always backs the `/actuator/prometheus` endpoint.

Examples:

```bash
# Health check (public) - use /actuator/health instead of /api/test/health
curl http://localhost:8080/actuator/health

# Health with full component details (ADMIN token)
curl http://localhost:8080/actuator/health -H "Authorization: Bearer <admin_jwt_token>"

# App info (public)
curl http://localhost:8080/actuator/info

# Metrics (ADMIN token)
curl http://localhost:8080/actuator/metrics -H "Authorization: Bearer <admin_jwt_token>"

# Prometheus scrape target (ADMIN token)
curl http://localhost:8080/actuator/prometheus -H "Authorization: Bearer <admin_jwt_token>"
```

## API Documentation

### Swagger UI

Open in your browser:

```
http://localhost:8080/swagger-ui.html
```

The standalone `swagger-ui.html` page fetches the OpenAPI specification from SpringDoc's default endpoint (`/v3/api-docs`). The UI provides interactive testing, an "Authorize" button for JWT setup, request and response examples, and schema documentation.

### Base Path

All API routes are prefixed with `/api`.

### Example Requests

```bash
# Get all tasks (requires authentication)
curl -X GET http://localhost:8080/api/tasks \
  -H "Authorization: Bearer <your_jwt_token>"

# Filter tasks by status (TODO | IN_PROGRESS | DONE)
curl -X GET "http://localhost:8080/api/tasks?status=DONE" \
  -H "Authorization: Bearer <your_jwt_token>"

# Filter tasks by priority (LOW | MEDIUM | HIGH)
curl -X GET "http://localhost:8080/api/tasks?priority=HIGH" \
  -H "Authorization: Bearer <your_jwt_token>"

# Combine status and priority filters
curl -X GET "http://localhost:8080/api/tasks?status=TODO&priority=HIGH" \
  -H "Authorization: Bearer <your_jwt_token>"

# Filter + paginate together
curl -X GET "http://localhost:8080/api/tasks?status=TODO&page=0&size=20" \
  -H "Authorization: Bearer <your_jwt_token>"

# Create a new task
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_jwt_token>" \
  -d '{"title":"Learn Spring Boot","description":"Complete the tutorial","dueDate":"2026-12-31"}'

# Create a task due well in the past — the response includes "overdue": true
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_jwt_token>" \
  -d '{"title":"Overdue task","dueDate":"2026-01-15"}'

# Filter to overdue tasks only
curl -X GET "http://localhost:8080/api/tasks?overdue=true" \
  -H "Authorization: Bearer <your_jwt_token>"

# Overdue composes with other filters
curl -X GET "http://localhost:8080/api/tasks?overdue=true&priority=HIGH" \
  -H "Authorization: Bearer <your_jwt_token>"

# Tasks due on or before a date (inclusive upper bound)
curl -X GET "http://localhost:8080/api/tasks?dueBefore=2026-06-01" \
  -H "Authorization: Bearer <your_jwt_token>"

# Tasks due on or after a date (inclusive lower bound)
curl -X GET "http://localhost:8080/api/tasks?dueAfter=2026-06-01" \
  -H "Authorization: Bearer <your_jwt_token>"

# Tasks due within an inclusive range
curl -X GET "http://localhost:8080/api/tasks?dueBefore=2026-06-30&dueAfter=2026-06-01" \
  -H "Authorization: Bearer <your_jwt_token>"

# Tasks due exactly on one day (inclusive bounds on the same date)
curl -X GET "http://localhost:8080/api/tasks?dueBefore=2026-06-15&dueAfter=2026-06-15" \
  -H "Authorization: Bearer <your_jwt_token>"

# Date range composes with status, priority, and overdue
curl -X GET "http://localhost:8080/api/tasks?dueBefore=2026-06-30&status=TODO&priority=HIGH" \
  -H "Authorization: Bearer <your_jwt_token>"

# Sort by title ascending
curl -X GET "http://localhost:8080/api/tasks?sortBy=title&direction=asc" \
  -H "Authorization: Bearer <your_jwt_token>"

# Sort by due date, newest deadline first
curl -X GET "http://localhost:8080/api/tasks?sortBy=dueDate&direction=desc" \
  -H "Authorization: Bearer <your_jwt_token>"

# Sort composes with filters and pagination (default sort is createdAt DESC)
curl -X GET "http://localhost:8080/api/tasks?status=TODO&sortBy=priority&direction=asc&page=0&size=20" \
  -H "Authorization: Bearer <your_jwt_token>"

# List all users as an administrator
curl -X GET "http://localhost:8080/api/admin/users?page=0&size=20" \
  -H "Authorization: Bearer <your_admin_jwt_token>"

# Promote a user to ADMIN
curl -X PATCH http://localhost:8080/api/admin/users/<id>/role \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_admin_jwt_token>" \
  -d '{"role":"ADMIN"}'
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_SECRET` | Base64-encoded 256-bit secret | required in all profiles |
| `JWT_EXPIRATION_MS` | Token lifetime in milliseconds | `86400000` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of allowed origins | `http://localhost:3000,http://localhost:5173` |
| `LOCKOUT_MAX_ATTEMPTS` | Failed logins before the account is locked | `5` |
| `LOCKOUT_DURATION_MS` | Lockout duration in milliseconds | `900000` |
| `RATE_LIMIT_ENABLED` | Enable IP-based rate limiting on auth endpoints | `true` |
| `RATE_LIMIT_MAX_REQUESTS` | Maximum requests per IP within the window | `10` |
| `RATE_LIMIT_WINDOW_MS` | Rate-limit window in milliseconds | `60000` |
| `ADMIN_USERNAME` | Username for the bootstrap admin account | `admin` |
| `ADMIN_PASSWORD` | Password for the bootstrap admin account; blank disables bootstrap | *(empty)* |
| `ADMIN_EMAIL` | Email for the bootstrap admin account | `admin@example.com` |
| `DB_HOST` | PostgreSQL host (docker profile) | `postgres` |
| `DB_PORT` | PostgreSQL port (docker profile) | `5432` |
| `DB_NAME` | PostgreSQL database name (docker profile) | `tododb` |
| `DB_USERNAME` | PostgreSQL user (docker profile) | `todo` |
| `DB_PASSWORD` | PostgreSQL password (docker profile) | `todo` |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `default` |

### H2 Console

Enabled only in the `dev` profile. Access the database at:

```
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:todo
User:     sa
Password: (leave empty)
```

## Project Structure

```
src/main/java/com/example/todolist/
|-- config/            # Spring configuration classes, admin bootstrap runner
|-- security/          # JWT filter and provider, rate limit filter, UserDetailsService
|-- controller/        # REST endpoints (@RestController)
|-- service/           # Business logic (@Service)
|-- repository/        # Data access layer (@Repository, JPA)
|-- entity/            # JPA entities (Task, User, RevokedToken)
|-- dto/               # Request/Response DTOs
|   |-- user/          # Registration, login, response, pagination, role update
|-- exception/         # Custom exceptions and global exception handler
```

Database migrations live in `src/main/resources/db/migration/` and are applied by Flyway.

The React frontend lives in `frontend/`:

```
frontend/
|-- src/
|   |-- api.js                # API client (auth + task + admin + profile endpoints)
|   |-- App.jsx               # Session state, top bar, Tasks/Profile/Admin view switching
|   |-- components/
|   |   |-- AuthScreen.jsx    # Login / register
|   |   |-- TaskBoard.jsx     # Task CRUD, filters, sorting, pagination
|   |   |-- TaskFilters.jsx   # Filter/sort controls shared by task boards
|   |   |-- ProfilePanel.jsx  # Self-service profile UI (email + password change)
|   |   |-- AdminPanel.jsx    # Admin user & task management UI
|   |   |-- StatusBar.jsx     # Health-check status bar
|   |-- styles.css
|-- package.json              # Vite + React
```

## Testing

### Run All Tests

```bash
./mvnw test
```

### Test Coverage

- **Unit tests**: Service and security layers (JUnit 5, Mockito).
- **API tests**: RestAssured-based integration tests covering authentication, CRUD, authorization, pagination, lockout, logout, admin, profile, and Actuator endpoints.
- **Security tests**: JWT generation, validation, and revocation.
- **PostgreSQL smoke test**: Testcontainers-backed tests proving Flyway migrations apply on a real PostgreSQL 16 instance and that Hibernate `ddl-auto: validate` accepts the PG-generated schema. Skipped automatically when Docker is unavailable; run in CI.

### Test Examples

```bash
# Run only unit tests
./mvnw test -Dtest="*Test"

# Run tests with the test profile
./mvnw test -Dspring.profiles.active=test
```

### Test Structure

- `src/test/java/com/example/todolist/`
  - `api/tests/` - End-to-end API flow tests (RestAssured)
  - `controller/` - REST endpoint integration tests
  - `service/` - Business logic tests
  - `security/` - Authentication and authorization tests
  - `util/` - Test helpers and base classes

## Roadmap

- **Refresh Tokens**: long-lived refresh tokens combined with short-lived access tokens.
- **Audit Logging**: entity change tracking with creation and modification timestamps.
- **Caching**: Redis-backed caching for performance optimization.
- **API Versioning**: support for multiple API versions.

Note: CI is already in place (see `.github/workflows/ci.yml`): on every push/PR to `main` it runs `./mvnw verify` on JDK 17, including the Testcontainers PostgreSQL smoke test, and uploads the surefire reports as an artifact.
