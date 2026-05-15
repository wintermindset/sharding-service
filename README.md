# Sharding Service

A centralized shard index lookup/mapping service for distributed database sharding architectures. Built with Java 21, Spring Boot 4.0.6 and PostgreSQL.

## Architecture

This service acts as a **shard directory** — it stores and manages mappings from logical object identifiers (`object_id`) to shard numbers (`shard_index`), telling clients which shard holds a given piece of data. Strings for `object_id` type were chosen because both UUID and Long can be written as strings.

## Modules

| Module | Description |
|---|---|
| `sharding-core` | REST API service with JPA, Flyway, Micrometer + Prometheus |
| `load-generator` | Load-testing tool that generates traffic against the API |

## Tech Stack

- **Java 21**, Spring Boot 4.0.6, Spring Data JPA
- **PostgreSQL 16** (H2 for tests)
- **Flyway** for database migrations
- **Error Prone** + **NullAway** for static analysis
- **Micrometer** + **Prometheus** + **Grafana** for monitoring
- **Docker** + **Docker Compose**

## Database

Single table `shard_indices`:

| Column | Type | Description |
|---|---|---|
| `id` | `BIGSERIAL` | Auto-increment PK |
| `object_id` | `VARCHAR(64)` | Unique logical object identifier |
| `shard_index` | `INTEGER` | Shard assignment (0–63) |
| `version` | `BIGINT` | Optimistic locking |

A Flyway migration seeds 5,000,000 sample records on first startup.

## REST API

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/shard-indices` | Create a new shard mapping |
| `GET` | `/api/v1/shard-indices/{objectId}` | Look up shard for an object |
| `PUT` | `/api/v1/shard-indices/{objectId}` | Move an object to a different shard |

## Getting Started

### Prerequisites

- Java 21 (SDKMAN recommended)
- Docker + Docker Compose
- Maven (or use `./mvnw`)

### Run

```bash
# Start PostgreSQL, Prometheus, Grafana
docker compose -f docker/dev/docker-compose.yml up -d

# Build and run the service
./mvnw spring-boot:run -pl sharding-core
```

The service starts on port `8080`. Flyway automatically creates the table and seeds 5M records.

### Run tests

```bash
./mvnw test -pl sharding-core
```

### Run load generator

```bash
# Start the full test environment (service + DB + monitoring)
docker compose -f docker/test/docker-compose.yml up -d

# Or run the load generator directly
./mvnw spring-boot:run -pl load-generator
```

### Docker build

```bash
docker build -f Dockerfile.service -t sharding-service .
docker build -f Dockerfile.load-generator -t load-generator .
```

## Monitoring

- **Prometheus** metrics: `http_server_requests_seconds`, `hikaricp_connections_*`, JVM metrics
- **Grafana** dashboards: TPS, P95 latency, CPU, heap, DB connections
- Default endpoints:
  - Service: `http://localhost:8080`
  - Prometheus: `http://localhost:9090`
  - Grafana: `http://localhost:3000`

## Configuration

Key properties in `application.yaml`:

| Property | Default | Description |
|---|---|---|
| `app.shard-index.retry.max-attempts` | `3` | Optimistic lock retry count |
| `app.shard-index.retry.delay-ms` | `50` | Delay between retries |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/sharding` | Database URL |

## Project Structure

```
sharding-service/
├── sharding-core/           # Main service module
│   └── src/main/resources/
│       ├── db/migration/    # Flyway migrations
│       └── application.yaml
├── load-generator/          # Load testing module
├── docker/
│   ├── dev/                 # Dev environment (PostgreSQL + Prometheus + Grafana)
│   └── test/                # Test environment (full stack)
├── Dockerfile.service
└── Dockerfile.load-generator
```

## Report for SFedU

![report1](/docs/report1.png)
![report2](/docs/report2.png)