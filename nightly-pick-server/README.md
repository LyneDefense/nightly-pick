# nightly-pick-server

Java Spring Boot backend for `夜拾`.

## Stack

- Java 17
- Spring Boot
- MyBatis-Plus
- PostgreSQL
- Flyway

## Requirements

- JDK 17
- Maven 3.6+

## Run

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.12.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
mvn spring-boot:run
```

Default datasource config expects:

```bash
DB_URL=jdbc:postgresql://localhost:5432/nightly_pick
DB_USERNAME=nightly_pick
DB_PASSWORD=nightly_pick
```

Flyway will run automatically on startup.

## Local PostgreSQL

Start PostgreSQL with Docker:

```bash
docker compose up -d
```

Then run the backend with the local profile:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.12.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Or simply:

```bash
./scripts/run-local.sh
```

If you prefer env vars, copy `.env.example` and export the values before startup.

## Test

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.12.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
mvn test
```

Or:

```bash
./scripts/test.sh
```

Tests use H2 in PostgreSQL compatibility mode with the same Flyway scripts.

## Current MVP APIs

- `POST /auth/login`
- `GET /health`
- `POST /conversations`
- `POST /conversations/{id}/messages`
- `POST /conversations/{id}/complete`
- `GET /records`
- `GET /records/{id}`
- `PATCH /records/{id}`
- `DELETE /records/{id}`
- `GET /me/settings`
- `PATCH /me/settings`
- `POST /me/clear-memories`
- `POST /me/clear-history`
- `GET /memories`
- `POST /audio/upload`
- `POST /audio/transcribe`
