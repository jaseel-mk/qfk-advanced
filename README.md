# QFK Community Platform

A production-oriented foundation for Qatar Football Koottam: a public community site, member experience, management platform and mobile-first Match Command Center. QFK is the parent community; Qatar United FC is its first competitive team.

## What is included

- Premium responsive public website at 320–1440+ px
- Member and admin dashboard experiences
- Match Command Center overview and registration views
- Java 21 Spring Boot feature-first backend foundation
- PostgreSQL/Flyway multi-team data model
- Pessimistic-lock registration foundation to prevent final-slot overbooking
- Central API validation errors and deny-by-default Spring Security
- Docker images, Compose stack and environment template

The interface currently uses representative development data to make the Phase 1 product experience fully inspectable. Authentication/API wiring is explicitly identified as the next foundation milestone; do not deploy publicly until it is complete.

## Required software

The easiest route is Docker Desktop with Docker Compose. For local development, install Node.js 22+, Java 21, Maven 3.9+ and PostgreSQL 16+.

## Run the frontend

```bash
npm install
npm run dev
```

Open `http://localhost:5173`. Use **Member login** for the admin shell and **Join QFK** for the member experience. The Command Center is available from the management sidebar and dashboard actions.

## Run the backend

Create a PostgreSQL database named `qfk`, copy `.env.example` to `.env`, then set secure local values.

```bash
cd backend
mvn spring-boot:run
```

Swagger UI is available at `http://localhost:8080/swagger-ui/index.html` once the backend is running.

## Run everything with Docker

```bash
docker compose up --build
```

Frontend: `http://localhost:5173`  
Backend: `http://localhost:8080`

Never use the development defaults in a shared or production environment. Generate a long random `JWT_SECRET` and strong database password.

## Tests and builds

```bash
npm run build
cd backend
mvn test
```

## Project structure

```text
src/
  app-entry.tsx              React application entry point
  global-styles.css          Shared application styles
  alternate-home.tsx         Alternate landing-page design
  alternate-home.css         Alternate landing-page styles
public/images/               Images served as static assets
backend/src/main/java/com/qfk
  common/                    Cross-cutting API/security
  member/                    Community members
  team/                      QFK teams
  match/                     Matches and registration rules
backend/src/main/resources/db/migration
                             Versioned PostgreSQL schema
IMPLEMENTATION_ASSESSMENT.md Architecture and migration assessment
```

## Deployment

Use the supplied images behind HTTPS. Configure `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JWT_SECRET` and `FRONTEND_URL` through your deployment platform's secret manager. Run Flyway automatically with one backend instance before scaling application replicas. Back up PostgreSQL and test restoration before launch.

## Next milestone

Finish JWT access/refresh rotation, persistent users/roles, DTO-based endpoints, frontend API adapters and security tests. Then proceed with Phase 2 match creation, registration/waitlist history, payments and attendance.

Suggested commit: `feat: establish qfk platform foundation and command center`
