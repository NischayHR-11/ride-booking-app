# Driver Service

Manages driver registration, availability, location updates, and automatic ride assignment. Consumes ride-requested events via Atropos webhook and publishes assignment results.

## Tech Stack

- Java 17
- Spring Boot (via `zeta-spring-boot-pom:4.1.14`)
- PostgreSQL
- Atropos SDK (`atropos-client:2.2.13`)
- Lombok

## Architecture

```
┌──────────────────────────────────────────────────────┐
│                  Driver Service                        │
│                                                       │
│  Atropos Webhook ─→ EventConsumer                    │
│                          ↓                            │
│  Controller → Service → Repository (PostgreSQL)       │
│                  ↓                                    │
│     NearestDriverMatchingStrategy (Haversine)        │
│                  ↓                                    │
│           EventPublisher → Atropos (Kinesis)         │
└──────────────────────────────────────────────────────┘
```

## API Endpoints

### Public APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/drivers` | Register a new driver |
| `GET` | `/api/drivers/{driverId}` | Get driver details |
| `PATCH` | `/api/drivers/{driverId}/availability` | Toggle driver availability |
| `PATCH` | `/api/drivers/{driverId}/location` | Update driver GPS coordinates |

### Internal Webhooks (Atropos)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/drivers/events/ride-requested/webhook` | Receives `RIDE_REQUESTED` event |

## Events

### Consumed

| Event | Source | Via |
|-------|--------|-----|
| `RIDE_REQUESTED` | ride-service | Atropos webhook |

### Published

| Event | Topic | Trigger |
|-------|-------|---------|
| `DRIVER_ASSIGNED` | `_system_0_driver-assigned` | Nearest driver found and assigned |
| `NO_DRIVER_AVAILABLE` | `_system_0_no-driver-available` | No available driver within range |

## Driver Matching

Uses **Haversine formula** to find the nearest available driver:
1. Receives ride-requested event with pickup coordinates
2. Queries all available drivers
3. Calculates distance from each driver to pickup point
4. Assigns the closest driver (within configured radius)
5. Marks driver as unavailable

## Project Structure

```
src/main/java/com/ridebooking/driverservice/
├── DriverServiceApplication.java
├── config/          # Atropos client configuration
├── controller/      # REST + Webhook controllers
├── dto/             # Request/Response DTOs
├── entity/          # JPA entities (Driver)
├── events/          # Event models, publisher & consumer
├── exception/       # Global exception handler
├── repository/      # Spring Data JPA repositories
└── service/         # Business logic + matching strategy
```

## Prerequisites

- Java 17
- Maven 3.8+
- PostgreSQL 15
- Zeta Artifactory credentials

## Local Setup

```bash
# 1. Set Artifactory credentials
export MAVEN_REPO_USERNAME=<your-username>
export MAVEN_REPO_PASSWORD=<your-password>

# 2. Start PostgreSQL (if not already running)
docker run -d --name ride-pg \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=ride_booking_db \
  -p 5432:5432 postgres:15

# 3. Build
mvn clean package -DskipTests -s .mvn/settings.xml

# 4. Run
mvn spring-boot:run -s .mvn/settings.xml
```

Service starts on **port 8082**.

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8082` | HTTP port |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/ride_booking_db` | DB URL |
| `atropos.client.endpoint` | (env-specific) | Atropos endpoint |

## CI/CD

- Jenkins pipeline: `ci.JenkinsFile`
- Uses `MavenDockerPublish` shared library
- Publishes Docker image + Helm chart
- SonarQube scan enabled

## Related Services

- [ride-service](../ride-service) — Publishes `RIDE_REQUESTED` events
- [notification-service](../notification-service) — Consumes `DRIVER_ASSIGNED` events
- [cluster-spec](../cluster-spec) — Atropos topic/subscription definitions
