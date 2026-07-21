[![CI](https://github.com/HernaniSamuel/munehisa-investment-simulator/actions/workflows/ci.yml/badge.svg)](https://github.com/HernaniSamuel/munehisa-investment-simulator/actions/workflows/ci.yml)

# Munehisa Investment Simulator

An investment simulator named after **Munehisa Homma** (Sokyu Honma), the 18th-century Japanese rice merchant credited as the father of candlestick chart analysis. The project aims to let users practice trading strategies in a risk-free simulated market.

This repository contains the backend, the frontend, and a Python data-fetching microservice. It's also being used as a hands-on exercise in building a production-shaped Spring Boot service: layered architecture, real integration tests against Postgres, CI, and OpenAPI docs. See [`src/frontend/README.md`](src/frontend/README.md) for the frontend module and [`src/data-service/README.md`](src/data-service/README.md) for the Python service that fetches and normalizes third-party market data.

## Tech stack

- **Java 21** / **Spring Boot 4.1** (Web MVC, Spring Security, Spring Data JPA, Validation, Mail)
- **PostgreSQL 16**, schema-versioned with **Flyway**
- **JWT** (`com.auth0:java-jwt`) for stateless authentication
- **Docker** / **Docker Compose** for local Postgres + pgAdmin
- **JUnit 5**, **Mockito**, and **Testcontainers** for unit and integration tests
- **GitHub Actions** for CI (build → unit tests → integration tests)
- **springdoc-openapi** for interactive API docs (Swagger UI)

## Running locally

**Prerequisites:** JDK 21, Docker (with Docker Compose), and the Maven wrapper (already vendored, no local Maven install needed).

1. Copy the environment template and fill in your own values. It's needed in two places, since `docker compose` reads `.env` from the repo root while the backend app reads it from `src/backend/.env`:
   ```
   cp src/backend/.env.example .env
   cp src/backend/.env.example src/backend/.env
   ```
   Every value must be filled for the app to start: `EMAIL_PASSWORD` and `JWT_SECRET` have no defaults and are required by the backend; `POSTGRES_USER`/`POSTGRES_PASSWORD`/`POSTGRES_DB`/`POSTGRES_PORT` and `PGADMIN_DEFAULT_EMAIL`/`PGADMIN_DEFAULT_PASSWORD`/`PGADMIN_PORT` are required by Docker Compose (root `.env`) and must match the values used in `src/backend/.env`. Everything else in the template is optional and already has a sane default.
2. Start Postgres (and pgAdmin, optional) via Docker Compose:
   ```
   docker compose up -d
   ```
3. Run the backend:
   ```
   cd src/backend
   ./mvnw spring-boot:run
   ```
   The API listens on `http://localhost:8000` by default.

### API docs (Swagger UI)

Enabled by default when running with the `dev` profile (the default), no extra configuration needed - just open:

```
http://localhost:8000/swagger-ui.html
```

It's disabled by default in any other profile (e.g. a future `prod` profile). Set `SWAGGER_UI_ENABLED=false` in `.env` to opt out even in dev.

### Running tests

```
cd src/backend
./mvnw test -DexcludedGroups=integration   # fast, no Docker required
./mvnw test -Dgroups=integration           # needs Docker running (Testcontainers)
```

## Architecture

Single Maven module at `src/backend`, organized by responsibility:

```
controllers/   REST endpoints (HTTP concerns only, delegate to services)
service/       Business logic
repository/    Spring Data JPA repositories
domain/        JPA entities
dto/           Request/response records - entities are never exposed directly
exceptions/    Domain-specific exceptions, mapped to HTTP statuses by infra/RestExceptionHandler
infra/         Cross-cutting config: security (JWT filter, Spring Security), CORS, OpenAPI, error handling
```

Request flow: `Controller → Service → Repository`. Business rules and validation live in the service layer, which is covered by pure Mockito unit tests; `Controller` + `Repository` behavior is covered by Testcontainers-backed integration tests that run against a real PostgreSQL instance.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for branch naming, commit message format, PR/review flow,
and merge strategy. Issues use the template at
[.github/ISSUE_TEMPLATE.md](.github/ISSUE_TEMPLATE.md); PRs use the template at
[.github/PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md).
