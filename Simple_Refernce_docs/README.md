# Ride Booking Platform — Event-Driven Microservices

A production-quality backend system using **Spring Boot**, **PostgreSQL**, and **Atropos** (Zeta's internal event streaming platform) for inter-service communication.

## Architecture

```
┌─────────────────┐       ┌─────────────────┐       ┌──────────────────────┐
│   Ride Service  │       │  Driver Service  │       │ Notification Service │
│   (port 8081)   │       │   (port 8082)    │       │    (port 8083)       │
└────────┬────────┘       └────────┬─────────┘       └──────────┬───────────┘
         │                         │                             │
         │  RIDE_REQUESTED ─────►  │ (webhook consumer)          │
         │                         │                             │
         │                         │  DRIVER_ASSIGNED ──────────►│ (webhook consumer)
         │  RIDE_CANCELLED ────────┼─────────────────────────────►│
         │  RIDE_COMPLETED ────────┼─────────────────────────────►│
         │                         │                             │
         └─────────────────────────┴─────────────────────────────┘
                              ATROPOS (Kinesis-backed event bus)
```

## Services

| Service | Port | Responsibilities |
|---------|------|-----------------|
| **ride-service** | 8081 | Ride lifecycle (create, cancel, complete). Publishes: `RIDE_REQUESTED`, `RIDE_CANCELLED`, `RIDE_COMPLETED` |
| **driver-service** | 8082 | Driver registration, availability, location, matching. Consumes: `RIDE_REQUESTED`. Publishes: `DRIVER_ASSIGNED`, `NO_DRIVER_AVAILABLE` |
| **notification-service** | 8083 | Stores and serves notifications. Consumes: `DRIVER_ASSIGNED`, `RIDE_CANCELLED`, `RIDE_COMPLETED` |

## Atropos Topics

| Topic Name | Publisher | Consumer |
|------------|-----------|----------|
| `ride-requested` | ride-service | driver-service |
| `driver-assigned` | driver-service | notification-service |
| `no-driver-available` | driver-service | — (observable on AKHQ) |
| `ride-cancelled` | ride-service | notification-service |
| `ride-completed` | ride-service | notification-service |

## API Endpoints

### Ride Service
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/rides` | Create a ride |
| GET | `/api/rides/{rideId}` | Get ride details |
| POST | `/api/rides/{rideId}/cancel` | Cancel a ride |
| POST | `/api/rides/{rideId}/complete` | Complete a ride |

### Driver Service
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/drivers` | Register driver |
| GET | `/api/drivers/{driverId}` | Get driver details |
| PATCH | `/api/drivers/{driverId}/availability` | Update availability |
| PATCH | `/api/drivers/{driverId}/location` | Update GPS location |

### Notification Service
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/notifications/{userId}` | Get user notifications |

## Tech Stack

- Java 17
- Spring Boot (via `zeta-spring-boot-pom` 4.1.14)
- Spring Data JPA + PostgreSQL
- Atropos Client SDK (`in.zeta.oms:atropos-client:2.2.13`)
- Kinesis transport mode
- Olympus SpectraLogger for structured logging
- Helm charts for Kubernetes deployment
- Docker (Amazon Corretto 17 Alpine)

## Local Setup

1. Start PostgreSQL:
   ```bash
   docker run -d --name ride-pg -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=ride_booking_db -p 5432:5432 postgres:15
   ```

2. Build each service:
   ```bash
   cd ride-service && mvn clean package -DskipTests
   cd ../driver-service && mvn clean package -DskipTests
   cd ../notification-service && mvn clean package -DskipTests
   ```

3. Run each service:
   ```bash
   cd ride-service && mvn spring-boot:run
   cd driver-service && mvn spring-boot:run
   cd notification-service && mvn spring-boot:run
   ```

4. Access Swagger UIs:
   - Ride: http://localhost:8081/api/v1/ui
   - Driver: http://localhost:8082/api/v1/ui
   - Notification: http://localhost:8083/api/v1/ui

## Event Flow — Happy Path

1. **Client** → `POST /api/rides` → **Ride Service** creates ride, publishes `RIDE_REQUESTED`
2. **Atropos** → delivers event to **Driver Service** webhook → matches nearest driver
3. **Driver Service** → publishes `DRIVER_ASSIGNED`
4. **Atropos** → delivers event to **Notification Service** webhook → stores notification
5. Events are observable on **AKHQ**: https://akhq-appinfra.internal.mum1-pp.zetaapps.in/ui/atropos-kafka/topic

## Project Structure

```
ride-booking-app/
├── ride-service/          # Ride lifecycle microservice
├── driver-service/        # Driver management + matching
├── notification-service/  # Event consumer + notification store
├── cluster-spec/          # Kubernetes cluster spec (topics, subscriptions, routes)
└── README.md
```

## Design Patterns Used

- **Service Layer** — Business logic in `@Service` classes
- **Repository Pattern** — Spring Data JPA repositories
- **Event Publisher/Consumer** — Atropos SDK for async inter-service communication
- **DTO Pattern** — Request/Response DTOs separate from entities
- **Strategy Pattern** — `DriverMatchingStrategy` interface with `NearestDriverMatchingStrategy` impl
