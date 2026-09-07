# Driver Service — Deep Architectural Walkthrough
### Senior Staff Engineer Interview Preparation Guide

> **Target audience:** Candidates preparing for senior / staff backend engineer interviews.  
> **Stack:** Java 17 · Spring Boot 3 · Spring Data JPA · PostgreSQL · Flyway · Atropos (event bus) · Lombok · Swagger (SpringDoc) · JUnit 5 · Mockito · Docker · Helm / Kubernetes

---

## Table of Contents

1. [Service Overview](#1-service-overview)
2. [Project Structure & Package Design](#2-project-structure--package-design)
3. [Entry Point — `DriverServiceApplication.java`](#3-entry-point--driverserviceapplicationjava)
4. [Configuration Layer](#4-configuration-layer)
   - 4.1 [AtroposConfig.java](#41-atroposconfigjava)
   - 4.2 [SandboxConfig.java](#42-sandboxconfigjava)
5. [Entity Layer — `Driver.java`](#5-entity-layer--driverjava)
6. [Repository Layer — `DriverRepository.java`](#6-repository-layer--driverrepositoryjava)
7. [DTO Layer](#7-dto-layer)
   - 7.1 [DriverRegisterDto.java](#71-driverregisterdtojava)
   - 7.2 [DriverResponseDto.java](#72-driverresponsedtojava)
   - 7.3 [LocationUpdateDto.java](#73-locationupdatedtojava)
8. [Service Layer](#8-service-layer)
   - 8.1 [DriverMatchingStrategy.java (Interface)](#81-drivermatchingstrategy-interface)
   - 8.2 [NearestDriverMatchingStrategy.java](#82-nearestdrivermatchingstrategy)
   - 8.3 [DriverService.java](#83-driverservicejava)
9. [Event-Driven Layer](#9-event-driven-layer)
   - 9.1 [RideRequestedEventPayload.java](#91-riderequestedeventpayloadjava)
   - 9.2 [DriverAssignedEvent.java](#92-driverassignedeventjava)
   - 9.3 [NoDriverAvailableEvent.java](#93-nodriveravailableeventjava)
   - 9.4 [DriverEventPublisher.java](#94-drivereventpublisherjava)
10. [Provider Layer](#10-provider-layer)
    - 10.1 [DriverProvider.java](#101-driverproviderjava)
    - 10.2 [UserProvider.java](#102-userproviderjava)
    - 10.3 [RideProvider.java](#103-rideproviderjava)
11. [Exception Handling Layer](#11-exception-handling-layer)
    - 11.1 [DriverNotFoundException.java](#111-drivernotfoundexceptionjava)
    - 11.2 [GlobalExceptionHandler.java](#112-globalexceptionhandlerjava)
12. [Controller Layer](#12-controller-layer)
    - 12.1 [HealthController.java](#121-healthcontrollerjava)
    - 12.2 [DriverController.java (Full Endpoint Analysis)](#122-drivercontrollerjava)
13. [Database Migration — `V1.0.1__driver_tables.sql`](#13-database-migration)
14. [Test Suite](#14-test-suite)
    - 14.1 [DriverControllerTest.java](#141-drivercontrollertestjava)
    - 14.2 [SandboxProviderTest.java](#142-sandboxprovidertestjava)
15. [Design Patterns Catalogue](#15-design-patterns-catalogue)
16. [Runtime Request Flow (End-to-End)](#16-runtime-request-flow-end-to-end)
17. [Hidden Framework Magic](#17-hidden-framework-magic)
18. [Event-Driven Architecture Deep Dive](#18-event-driven-architecture-deep-dive)
19. [Diagrams](#19-diagrams)
20. [Interview Deep Dive — Tough Questions](#20-interview-deep-dive--tough-questions)
21. [How to Impress the Interviewer](#21-how-to-impress-the-interviewer)
22. [Potential Improvements](#22-potential-improvements)
23. [Deployment & Containerization (Dockerfile + Helm)](#23-deployment--containerization)

---

## 1. Service Overview

The **Driver Service** is a standalone Spring Boot microservice within a ride-booking platform. It owns:

| Responsibility | Details |
|---|---|
| **Driver registration** | Persist a new driver record with vehicle info, starting location, and initial availability |
| **Availability management** | Toggle a driver's on/off-duty flag |
| **Location tracking** | Accept real-time GPS coordinate updates |
| **Ride assignment** | Consume `RIDE_REQUESTED` webhook events, run a matching algorithm, mark the best driver busy, publish `DRIVER_ASSIGNED` or `NO_DRIVER_AVAILABLE` back to the event bus |
| **Query APIs** | List all drivers, list only available drivers, fetch a single driver by ID |

**Why a separate microservice?**  
The Single Responsibility Principle at the service level. Driver lifecycle management is a bounded context — it has its own database table, its own event contracts, and its own deployment cadence. Coupling it into a monolith would violate SRP and hinder independent scaling.

---

## 2. Project Structure & Package Design

```
com.ridebooking.driverservice
├── DriverServiceApplication.java     ← Entry point + component scan root
├── config/
│   └── AtroposConfig.java            ← Spring @Configuration for external SDK beans
├── controller/
│   ├── DriverController.java         ← REST API surface
│   └── HealthController.java         ← Liveness probe endpoint
├── dto/
│   ├── DriverRegisterDto.java        ← Inbound: register payload
│   ├── DriverResponseDto.java        ← Outbound: API response contract
│   └── LocationUpdateDto.java        ← Inbound: location patch payload
├── entity/
│   └── Driver.java                   ← JPA-managed DB entity
├── events/
│   ├── DriverAssignedEvent.java      ← Outbound event schema
│   ├── DriverEventPublisher.java     ← Publishes events to Atropos
│   ├── NoDriverAvailableEvent.java   ← Outbound event schema
│   └── RideRequestedEventPayload.java← Inbound webhook event schema
├── exception/
│   ├── DriverNotFoundException.java  ← Domain-specific exception
│   └── GlobalExceptionHandler.java  ← Centralised error translation
├── provider/
│   ├── DriverProvider.java           ← Sandbox RBAC object provider
│   ├── RideProvider.java             ← Sandbox RBAC object provider
│   ├── SandboxConfig.java            ← Wires all providers
│   └── UserProvider.java             ← Sandbox RBAC object provider
├── repository/
│   └── DriverRepository.java        ← Spring Data JPA repository
└── service/
    ├── DriverMatchingStrategy.java   ← Strategy interface
    ├── DriverService.java            ← Business logic orchestrator
    └── NearestDriverMatchingStrategy.java ← Concrete strategy
```

**Layered Architecture (Onion / Hexagonal flavour):**

```
HTTP Request
    ↓
Controller (adapter)
    ↓
Service (business logic)
    ↓
Repository (port/adapter to DB)
    ↓
Database (PostgreSQL)
```

Each layer depends only on the layer directly below it and communicates upward through return values or exceptions — no circular dependencies.

> **Important Interview Point:** This is the classic _Ports and Adapters_ (Hexagonal) pattern. The service layer is the core; everything else is an adapter.

---

## 3. Entry Point — `DriverServiceApplication.java`

```java
package com.ridebooking.driverservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
        "in.zeta.springframework.boot.commons",
        "com.ridebooking.driverservice"
})
public class DriverServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DriverServiceApplication.class, args);
    }
}
```

### Line-by-line Explanation

**`package com.ridebooking.driverservice;`**  
Declares the Java package. Spring's component scan will use the root package to discover beans annotated with `@Component`, `@Service`, `@Repository`, `@Controller`, etc. This is the _anchor package_.

**`@SpringBootApplication`**  
This is a _meta-annotation_ that bundles three annotations in one:

| Composed Annotation | What it Does |
|---|---|
| `@SpringBootConfiguration` | Marks this class as a `@Configuration` — a source of `@Bean` definitions |
| `@EnableAutoConfiguration` | Triggers Spring Boot's autoconfiguration machinery. Reads `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` from all JARs on the classpath and conditionally configures DataSource, JPA, MVC, Actuator, etc. |
| `@ComponentScan` | Scans the package tree for Spring-managed components |

> **Framework Magic:** `@EnableAutoConfiguration` uses `@Conditional` annotations internally. For example, `DataSourceAutoConfiguration` is only applied if a `javax.sql.DataSource` is not already defined and a JDBC driver is on the classpath. This is resolved at startup via `SpringFactoriesLoader` / `ImportAutoConfigurationImportSelector`.

> **Interview Question:** "What is the difference between `@SpringBootApplication` and `@EnableAutoConfiguration`?" — `@SpringBootApplication` is a convenience composite; `@EnableAutoConfiguration` is one of its constituents that can be used standalone.

**`@ComponentScan(basePackages = {...})`**  
Even though `@SpringBootApplication` already includes `@ComponentScan`, this explicit declaration _extends_ the scan to include `in.zeta.springframework.boot.commons`. This is why it doesn't conflict — Spring merges the package lists.  

Why does `in.zeta.springframework.boot.commons` need scanning? This is a **platform SDK** (Zeta's internal Spring Boot commons library) that ships reusable components like `SandboxAccessControlProvider`, interceptors, and security infrastructure. Because it lives in a different package hierarchy, the default scan would miss it.

> **Alternative approach:** The SDK could use `META-INF/spring.factories` or `@AutoConfiguration` to self-register beans without requiring the caller to explicitly scan it. This is a more production-grade approach used in well-designed SDKs.

**`SpringApplication.run(DriverServiceApplication.class, args);`**  
Bootstraps the Spring application context:
1. Creates a `SpringApplication` instance
2. Detects the application type (Servlet-based because `spring-boot-starter-web` is on the classpath)
3. Loads `ApplicationContext` — `AnnotationConfigServletWebServerApplicationContext` in this case
4. Runs all `ApplicationContextInitializer`s
5. Publishes `ApplicationStartingEvent` → loads configuration → creates beans → starts embedded Tomcat → publishes `ApplicationReadyEvent`

> **JVM Implication:** `SpringApplication.run()` runs on the **main thread**. Tomcat creates a separate `acceptor` thread that listens on port 8080. Each HTTP request is dispatched to a thread from Tomcat's **thread pool** (default: 10 min, 200 max threads with Spring Boot defaults).

> **Memory Implication:** The Spring `ApplicationContext` (IoC container) is a `ConcurrentHashMap`-backed bean registry. Every singleton bean is stored here for the lifetime of the JVM process. The heap impact depends on the number of beans and their state.

---

## 4. Configuration Layer

### 4.1 `AtroposConfig.java`

```java
@Configuration
public class AtroposConfig {

    @Bean
    public AtroposPublisherClient atroposPublisherClient() {
        return new AtroposPublisherClient();
    }

    @Bean
    public Gson gson() {
        return new Gson();
    }
}
```

**Purpose:** Explicitly declares beans for external SDK clients that are not Spring-aware and therefore cannot be picked up by component scanning.

**`@Configuration`**  
Signals to Spring that this class is a _bean factory_. Under the hood, Spring creates a **CGLIB subclass** (proxy) of this class. This means every call to `atroposPublisherClient()` from within another `@Bean` method returns the **same singleton instance** from the context — not a new object. This is called **full configuration mode** (as opposed to `@Component`-annotated classes which are _lite_ mode).

> **Senior Engineer Insight:** If you annotate a configuration class with `@Configuration(proxyBeanMethods = false)`, Spring skips CGLIB proxying. This is faster at startup but breaks inter-`@Bean` method call singleton guarantees. Use it only when bean methods are truly independent.

**`@Bean public AtroposPublisherClient atroposPublisherClient()`**  
- Atropos is Zeta's internal publish/subscribe event routing SDK. It does not carry a `@Component` annotation, so Spring cannot auto-detect it.
- By declaring it as a `@Bean`, it becomes a singleton managed by Spring. `DriverEventPublisher` receives it via constructor injection.
- **Lifecycle:** Instantiated during context refresh, destroyed on context close (if the client implements `DisposableBean` or a `@PreDestroy` method).

**`@Bean public Gson gson()`**  
Gson is Google's JSON serialization library. Declared here so that:
1. A single `Gson` instance is shared application-wide (thread-safe by default).
2. It can be injected wherever needed without instantiating a new `Gson` each time (avoids GC pressure).

> **Alternative:** Jackson (`ObjectMapper`) is the Spring Boot default and is already auto-configured. Using both Gson and Jackson in the same application adds redundancy. A potential improvement would be to unify on Jackson (which is already injected in `DriverController`). However, the Atropos SDK likely requires Gson internally, hence both are present.

---

### 4.2 `SandboxConfig.java`

```java
@Configuration
public class SandboxConfig {

    @Bean
    @Primary
    public SandboxAccessControlProvider getSandboxAccessControlProvider(
            UserProvider userProvider,
            DriverProvider driverProvider,
            RideProvider rideProvider,
            SandboxAccessControlProvider sacp) {
        sacp.registerObjectProvider(UserProvider.OBJECT_TYPE, userProvider);
        sacp.registerObjectProvider(DriverProvider.OBJECT_TYPE, driverProvider);
        sacp.registerObjectProvider(RideProvider.OBJECT_TYPE, rideProvider);
        return sacp;
    }
}
```

**Purpose:** Wires the Sandbox RBAC (Role-Based Access Control) framework with domain-specific object providers. The Sandbox framework (part of Zeta's platform) uses the `SandboxAccessControlProvider` to resolve objects when authorizing API calls annotated with `@SandboxAuthorizedSync`.

**`@Primary`**  
When multiple beans of the same type exist in the context, `@Primary` instructs Spring to prefer this bean for injection without requiring `@Qualifier` on the injection site. Since the Zeta SDK likely auto-registers a default `SandboxAccessControlProvider`, `@Primary` overrides it with the enriched version.

**Method parameters = Constructor injection of collaborators**  
Spring sees four parameters and resolves each from the context:
- `UserProvider`, `DriverProvider`, `RideProvider` — all `@Component`-annotated, auto-scanned
- `SandboxAccessControlProvider sacp` — auto-registered by Zeta SDK auto-configuration

**`sacp.registerObjectProvider(...)` calls**  
Register object type strings (`"driver"`, `"rider"`, `"ride"`) to their respective `ObjectProvider` implementations. When `@SandboxAuthorizedSync` authorizes a call, it resolves the object by type, calls `getObject()` on the provider, and checks the result against the caller's permissions.

> **Design Pattern:** **Registry / Service Locator** for object providers. `SandboxAccessControlProvider` acts as a registry.

---

## 5. Entity Layer — `Driver.java`

```java
@Entity
@Table(name = "drivers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver {

    @Id
    private String driverId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(nullable = false, unique = true)
    private String vehicleNumber;

    @Column(nullable = false)
    private String vehicleType;

    private double latitude;
    private double longitude;

    @Column(nullable = false)
    private boolean available;

    private double rating;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (driverId == null) driverId = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (rating == 0) rating = 5.0;
        available = true;
    }
}
```

### ORM Mapping Analysis

**`@Entity`**  
Marks this class as a JPA entity. Hibernate scans for it and registers it in the `EntityManagerFactory`'s metamodel. At startup, Hibernate creates a `ClassMetadata` object for this class describing every column mapping — no reflection at query time.

**`@Table(name = "drivers")`**  
Maps this entity to the `drivers` PostgreSQL table. Without this, Hibernate would default to the class name (`driver`). Explicit naming is a best practice because it:
- Makes the schema contract explicit in the code
- Avoids issues if the class is renamed via refactoring

**Lombok Annotations**

| Annotation | Generated code | Why |
|---|---|---|
| `@Getter` | `getX()` for all fields | Allows Hibernate and service code to read fields |
| `@Setter` | `setX()` for all fields | Required for `updateAvailability` and `updateLocation` mutations |
| `@NoArgsConstructor` | `Driver()` | JPA spec mandates a no-arg constructor for proxy creation |
| `@AllArgsConstructor` | `Driver(all fields)` | Required by `@Builder` internally |
| `@Builder` | `Driver.builder()...build()` | Fluent construction in `DriverService.registerDriver` |

> **Framework Magic — Hibernate Proxy:** When Hibernate fetches a `Driver` lazily (e.g., via a relation from another entity), it does NOT return your class directly. Instead, it returns a **CGLIB proxy** subclass of `Driver`. This proxy overrides all getter methods to trigger a lazy SQL load. That is why the no-arg constructor is mandatory — the proxy subclass must call `super()`.

**`@Id private String driverId`**  
- Uses `String` (UUID) as the primary key instead of `Long` auto-increment.
- **Why UUID?** UUIDs are globally unique, making them safe for distributed systems where IDs might be generated across multiple service instances without coordination. They also don't expose sequential business information (like how many drivers have registered).
- **Why not `@GeneratedValue`?** Because UUID generation is handled manually in `@PrePersist`, giving full control over the format (`UUID.randomUUID().toString()` produces a lowercase hyphenated string like `550e8400-e29b-41d4-a716-446655440000`).
- **Tradeoff:** UUID primary keys are 36 characters vs 8 bytes for `Long`. Index size grows. Random UUID insertion causes B-Tree index fragmentation (vs sequential `BIGSERIAL`). For high-write tables, `ULID` (Universally Unique Lexicographically Sortable Identifier) is a better alternative.

**`@Column(nullable = false, unique = true) private String phone`**  
- `nullable = false` adds a `NOT NULL` constraint at the DB level AND is respected by the JPA validation layer.
- `unique = true` creates a unique index on the `phone` column in PostgreSQL.
- Both constraints are enforced at the DB level even if code-level validation is bypassed.

**`@Column(name = "created_at", updatable = false)`**  
- `name = "created_at"` maps to the snake_case DB column (Hibernate convention: Java camelCase → SQL snake_case by default but explicit mapping is clearer).
- `updatable = false` means Hibernate will **never** include `created_at` in an `UPDATE` SQL statement. This is the correct implementation of an _immutable audit timestamp_.

> **Interview Question:** "What happens if you call `driver.setCreatedAt()` and then `save()`?"  
> **Answer:** Hibernate will generate the `UPDATE` SQL without the `created_at` column, so the DB value stays unchanged even though the Java object was mutated. The `updatable = false` constraint operates at the Hibernate SQL generation level, not the Java setter level.

**`@PrePersist protected void onCreate()`**  
A JPA entity lifecycle callback. Hibernate invokes this method just before executing the first `INSERT` for this entity. It runs within the same transaction.

```java
if (driverId == null) driverId = UUID.randomUUID().toString();
```
Generates a UUID only if one hasn't been set — allows tests to inject a known ID.

```java
if (createdAt == null) createdAt = LocalDateTime.now();
```
Sets the creation timestamp to the server's current time. Note: `LocalDateTime.now()` uses the JVM's system clock. In distributed deployments, clock skew between instances is a risk — `Instant.now()` with a time zone is safer.

```java
if (rating == 0) rating = 5.0;
available = true;
```
Initializes default values. Rating starts at 5.0 (perfect). Availability is always `true` on first registration — a new driver is ready to take rides.

> **Alternative:** These defaults could be set in the SQL DDL (`DEFAULT 5.0`, `DEFAULT TRUE`) or via `@ColumnDefault`. Using `@PrePersist` is better because it guarantees consistency even if the DB is accessed directly via SQL.

---

## 6. Repository Layer — `DriverRepository.java`

```java
@Repository
public interface DriverRepository extends JpaRepository<Driver, String> {
    List<Driver> findByAvailableTrue();
    boolean existsByPhone(String phone);
    boolean existsByVehicleNumber(String vehicleNumber);
    long countByAvailableTrue();
}
```

**`@Repository`**  
A specialization of `@Component` that adds:
1. Exception translation: Spring wraps raw `PersistenceException` into `DataAccessException` subclasses, making your service layer database-agnostic.
2. Semantic clarity: signals this bean is a persistence boundary.

> **Framework Magic:** `JpaRepository` is just an interface. Spring Data JPA creates a **dynamic proxy** (`SimpleJpaRepository` is the default implementation) at startup using `JdkProxyFactory`. When you call `driverRepository.save()`, you're calling a method on a JDK proxy that delegates to `SimpleJpaRepository.save()`, which internally calls `EntityManager.persist()` or `EntityManager.merge()`.

**`JpaRepository<Driver, String>`**  
Parameterized with the entity type (`Driver`) and the primary key type (`String` for UUID). Inherits:
- `save(entity)` → INSERT or UPDATE
- `findById(id)` → SELECT by PK
- `findAll()` → SELECT *
- `deleteById(id)` → DELETE
- `count()` → COUNT(*)
- And many more

### Query Derivation — How Spring Data JPA Does It

**`List<Driver> findByAvailableTrue()`**

Spring Data JPA parses the method name using the `PartTreeJpaQuery` parser:
- `findBy` → SELECT WHERE clause
- `Available` → maps to the `available` field on `Driver`
- `True` → the condition is `= true`

**Generated SQL:**
```sql
SELECT d.driver_id, d.name, d.phone, d.vehicle_number, d.vehicle_type,
       d.latitude, d.longitude, d.available, d.rating, d.created_at
FROM drivers d
WHERE d.available = true
```

> **Index Recommendation:** For a production table with millions of drivers, add a **partial index**:  
> `CREATE INDEX idx_drivers_available ON drivers(driver_id) WHERE available = true;`  
> A boolean column has terrible selectivity (~50%), so a full index helps little. A partial index covering only available drivers is far more efficient.

**`boolean existsByPhone(String phone)`**

Generated SQL (optimised by Spring Data JPA):
```sql
SELECT COUNT(*) > 0 FROM drivers WHERE phone = ?
```
Or more accurately, Hibernate generates:
```sql
SELECT 1 FROM drivers WHERE phone = ? LIMIT 1
```
This is faster than `findByPhone()` because it stops scanning as soon as one match is found.

**`boolean existsByVehicleNumber(String vehicleNumber)`**  
Same mechanism as `existsByPhone`.

**`long countByAvailableTrue()`**  
Generated SQL:
```sql
SELECT COUNT(*) FROM drivers WHERE available = true
```
Used in `getDriverStats()` to return system-level metrics without loading all driver objects into memory.

> **N+1 Problem Discussion:** This service does not have entity relations (no `@OneToMany` etc.), so N+1 is not a risk currently. If future enhancements add a `List<Ride> rides` to `Driver`, fetching a list of drivers with `FetchType.EAGER` would generate 1 query for drivers + N queries for each driver's rides. The fix: use `JOIN FETCH` in JPQL or `@EntityGraph`.

---

## 7. DTO Layer

### 7.1 `DriverRegisterDto.java`

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DriverRegisterDto {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Phone is required")
    private String phone;

    @NotBlank(message = "Vehicle number is required")
    private String vehicleNumber;

    @NotBlank(message = "Vehicle type is required")
    private String vehicleType;

    private double latitude;
    private double longitude;
}
```

**Why DTOs exist (Separation of Concerns):**

| Without DTO | With DTO |
|---|---|
| Entity fields exposed directly in API | API contract decoupled from DB schema |
| Adding `@Column` annotations pollutes API contract | Entity and DTO evolve independently |
| Cannot hide sensitive fields (e.g. internal IDs) | Full control over what is serialized |
| Validation annotations mixed with persistence | Clean separation of validation and ORM concerns |

**`@NotBlank`**  
A Bean Validation (JSR-380) annotation. `@NotBlank` is stricter than `@NotNull` — it also rejects empty strings and whitespace-only strings (`"   "` is blank). The validation is triggered by `@Valid` on the controller method parameter. If validation fails, Spring throws `MethodArgumentNotValidException`, caught by `GlobalExceptionHandler`.

**`double latitude`, `double longitude`**  
Using primitive `double` (not `Double`) — cannot be null. Default value is `0.0`. A driver registering without providing GPS coordinates will default to `(0.0, 0.0)` — the Gulf of Guinea off Africa's coast. This is a latent bug worth mentioning in interviews as an improvement opportunity. Validation annotations like `@DecimalMin` and `@DecimalMax` should be added for proper GPS range validation.

**`@Builder`**  
Lombok generates a nested static `Builder` class with fluent setters. This is the **Builder design pattern** — it allows constructing complex objects step-by-step without telescoping constructors. Test code and service code both benefit from readable, named-parameter construction.

---

### 7.2 `DriverResponseDto.java`

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DriverResponseDto {
    private String driverId;
    private String name;
    private String phone;
    private String vehicleNumber;
    private String vehicleType;
    private double latitude;
    private double longitude;
    private boolean available;
    private double rating;
    private LocalDateTime createdAt;
}
```

**API Contract Management:**  
This is the _outbound_ contract. Notice `driverId` is always included. For security-sensitive services, you might exclude `phone` or `vehicleNumber` from certain response contexts. Having a dedicated response DTO makes this straightforward — just remove the field without touching the entity.

**`LocalDateTime createdAt`**  
Jackson (the Spring Boot default serializer) will serialize `LocalDateTime` using ISO 8601 by default (`"2025-05-10T12:00:00"`). In production, consider annotating with `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")` for human-readable output, or switching to `Instant` for UTC-normalized timestamps that are timezone-agnostic.

**Versioning Consideration:**  
As the API evolves, you might need `DriverResponseDtoV2` with additional fields. DTOs make API versioning clean because the old DTO contract is preserved independently of the growing entity.

---

### 7.3 `LocationUpdateDto.java`

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class LocationUpdateDto {
    private double latitude;
    private double longitude;
}
```

**Minimal DTO for PATCH:**  
PATCH semantics mean only the fields being changed are included in the payload. Having a dedicated `LocationUpdateDto` instead of reusing `DriverRegisterDto` enforces the principle of **minimum necessary data**. The caller cannot accidentally update name or phone via a location endpoint.

**No `@Builder`:**  
Not strictly needed since this class has only 2 fields. `@AllArgsConstructor` is sufficient for test construction. This is a good example of not over-engineering.

---

## 8. Service Layer

### 8.1 `DriverMatchingStrategy` (Interface)

```java
public interface DriverMatchingStrategy {
    Optional<Driver> findBestDriver(List<Driver> availableDrivers, double pickupLat, double pickupLng);
}
```

**Design Pattern: Strategy**  
This interface is the _strategy contract_. It defines _what_ must be done (find the best driver) without specifying _how_. Any class implementing this interface is a valid matching strategy.

**Why `Optional<Driver>` return type?**  
Forces the caller (`DriverService`) to handle the case where no driver is found. If the method returned `Driver` (nullable), the caller might forget to null-check and throw an NPE in production. `Optional` makes the absence explicit and compiler-enforced.

**`List<Driver> availableDrivers` parameter:**  
The repository query has already filtered for available drivers. The strategy receives a pre-filtered list, keeping the strategy focused purely on _selection_ logic, not _filtering_ logic. This is good SRP.

> **Senior Engineer Insight:** The current design pre-loads ALL available drivers into memory before calling the strategy. For a large fleet (100,000+ drivers), this is a scalability problem. A better design passes query parameters (lat/lng + radius) to the strategy, which then constructs a DB query with a geo-spatial filter. PostGIS extension for PostgreSQL supports `ST_Distance` and `ST_DWithin` for this purpose.

---

### 8.2 `NearestDriverMatchingStrategy`

```java
@Component
public class NearestDriverMatchingStrategy implements DriverMatchingStrategy {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double MAX_SEARCH_RADIUS_KM = 5.0;

    @Override
    public Optional<Driver> findBestDriver(List<Driver> availableDrivers, double pickupLat, double pickupLng) {
        return availableDrivers.stream()
                .filter(d -> haversineDistance(...) <= MAX_SEARCH_RADIUS_KM)
                .min((d1, d2) -> { ... });
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
```

**`@Component`**  
Registers this class as a Spring-managed singleton. Because `DriverMatchingStrategy` has only **one implementation** in the context, Spring can inject it unambiguously.

**Haversine Distance Formula:**  
The Haversine formula calculates the great-circle distance between two points on a sphere given their latitude/longitude. It accounts for the Earth's curvature.

$$d = 2R \cdot \arctan2\left(\sqrt{a}, \sqrt{1-a}\right)$$
$$a = \sin^2\!\left(\frac{\Delta\phi}{2}\right) + \cos\phi_1 \cdot \cos\phi_2 \cdot \sin^2\!\left(\frac{\Delta\lambda}{2}\right)$$

Where $R = 6371$ km, $\phi$ = latitude, $\lambda$ = longitude.

**Three-tier Comparator — tie-breaking:**
1. **Primary: Haversine distance (ascending)** — nearest driver wins
2. **Secondary: Rating (descending)** — if equidistant, higher-rated driver wins
3. **Tertiary: Registration date (ascending)** — earliest registered driver wins as final tiebreak

**`private static final double EARTH_RADIUS_KM = 6371.0`**  
Static constants are stored in the class's static area of the Method Area (JVM). They are class-level, not instance-level — no memory overhead per object. `final` prevents accidental reassignment.

**`MAX_SEARCH_RADIUS_KM = 5.0`**  
Hardcoded 5 km radius. In production, this should be externalized to application properties and injected via `@Value("${driver.matching.radius.km:5.0}")` to allow environment-specific tuning without redeployment.

**Thread Safety:**  
`NearestDriverMatchingStrategy` is a singleton with no mutable state — all state is local to each method invocation. It is completely **thread-safe** as-is.

---

### 8.3 `DriverService.java`

```java
@Service
@RequiredArgsConstructor
@Transactional
public class DriverService {

    private static final SpectraLogger logger = OlympusSpectra.getLogger(DriverService.class);
    private static final String DRIVER_ID_ATTR = "driverId";

    private final DriverRepository driverRepository;
    private final DriverEventPublisher driverEventPublisher;
    private final DriverMatchingStrategy matchingStrategy;
    ...
}
```

**`@Service`**  
Specialization of `@Component`. Functionally equivalent to `@Component` for component scanning, but:
- Communicates intent: this class contains business logic
- May be targeted by AOP pointcuts that specifically match `@Service`-annotated beans
- Some Spring modules (like Spring Security) treat `@Service` specially

**`@RequiredArgsConstructor` (Lombok)**  
Generates a constructor with ALL `final` fields as parameters:
```java
public DriverService(
    DriverRepository driverRepository,
    DriverEventPublisher driverEventPublisher,
    DriverMatchingStrategy matchingStrategy) { ... }
```
Spring Boot detects a single constructor and automatically uses it for **constructor injection** — no `@Autowired` needed.

> **Why constructor injection over field injection (`@Autowired private DriverRepository repo`)?**
> 1. **Testability:** Constructor-injected dependencies can be passed in tests without a Spring context. Field injection requires reflection or a Spring test context.
> 2. **Immutability:** Final fields cannot be reassigned — guarantees the service's dependencies never change.
> 3. **Fail-fast:** Missing dependencies cause an immediate startup failure, not a `NullPointerException` at request time.
> 4. **Visibility:** The constructor documents all required collaborators explicitly.

**`@Transactional` (class-level)**  
Applies transaction semantics to ALL public methods in this class. Spring wraps each call in a transaction (BEGIN on enter, COMMIT on success, ROLLBACK on exception). The default propagation is `REQUIRED` — if the caller already has a transaction, join it; otherwise create a new one. The default isolation is `READ_COMMITTED` in most databases.

> **Framework Magic — Transaction Proxy:** When `DriverController` calls `driverService.registerDriver(dto)`, it is NOT calling `DriverService.registerDriver()` directly. Spring wraps the `DriverService` bean in a **CGLIB proxy**. The proxy intercepts the method call, begins a transaction via `TransactionInterceptor`, delegates to the real method, and commits or rolls back. This is **AOP (Aspect-Oriented Programming)** applied transparently.

> **Important Interview Point:** `@Transactional` only works when called from **outside** the bean (through the proxy). Self-invocation — calling `this.registerDriver()` from within the same class — bypasses the proxy and therefore the transaction. This is a classic Spring gotcha.

**`private static final SpectraLogger logger`**  
`static final` — one logger instance per class, shared across all instances. Logger fields should always be static. `OlympusSpectra.getLogger()` is likely a wrapper around a structured logging framework (e.g. Log4j2 with JSON appender). Using a custom logger wrapper allows structured attributes (`attr("key", "value")`) to be added, which is essential for log aggregation platforms like Kibana, Splunk, or CloudWatch Insights.

**`private static final String DRIVER_ID_ATTR = "driverId"`**  
Avoids hardcoded string literals across multiple logger calls — prevents typos and enables easy renaming. This is the **Constant Extraction** refactoring principle.

---

#### `registerDriver(DriverRegisterDto dto)`

```java
public DriverResponseDto registerDriver(DriverRegisterDto dto) {
    if (driverRepository.existsByPhone(dto.getPhone())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Driver with phone already exists: " + dto.getPhone());
    }
    if (driverRepository.existsByVehicleNumber(dto.getVehicleNumber())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Driver with vehicle number already exists: " + dto.getVehicleNumber());
    }

    Driver driver = Driver.builder()
            .name(dto.getName())
            .phone(dto.getPhone())
            .vehicleNumber(dto.getVehicleNumber())
            .vehicleType(dto.getVehicleType())
            .latitude(dto.getLatitude())
            .longitude(dto.getLongitude())
            .build();

    driver = driverRepository.save(driver);
    logger.info("[DriverService] Driver registered").attr(DRIVER_ID_ATTR, driver.getDriverId()).log();
    return mapToResponse(driver);
}
```

**Uniqueness check before insert:**  
Two `existsBy` calls are made before inserting. This is a **Check-Then-Act** pattern that has a **Time-of-Check-to-Time-of-Use (TOCTOU)** race condition: two concurrent requests with the same phone number could both pass the `existsByPhone` check and then both attempt insertion. The database's `UNIQUE` constraint on `phone` and `vehicleNumber` acts as the true enforcement mechanism — the second insert will throw a `DataIntegrityViolationException`.

> **Production improvement:** Wrap the `save()` in a try-catch for `DataIntegrityViolationException` and convert it to the appropriate `CONFLICT` response. The `existsBy` checks then serve as an early-exit optimization (cheaper than letting the DB reject it) but are not the sole guard.

**`Driver.builder()...build()`**  
Note that `driverId`, `createdAt`, `rating`, and `available` are NOT set here. They are all initialized in `@PrePersist`. This is intentional separation of concerns — the service doesn't set defaults; the entity manages its own invariants.

**`driverRepository.save(driver)`**  
Since `driver.getDriverId()` returns the UUID set by `@PrePersist` (which runs before the actual INSERT), `SimpleJpaRepository.save()` calls `EntityManager.persist()` (for new entities) rather than `merge()`. Hibernate then executes:
```sql
INSERT INTO drivers (driver_id, name, phone, ...) VALUES (?, ?, ?, ...)
```

**`mapToResponse(driver)`**  
A private helper that manually maps `Driver` → `DriverResponseDto`. This manual mapping is explicit and readable. Alternative: **MapStruct** (annotation processor-based mapper) or **ModelMapper** (reflection-based). Manual mapping is preferred for simple cases because it's transparent and has no runtime overhead.

---

#### `handleRideRequested(RideRequestedEventPayload payload)`

```java
public void handleRideRequested(RideRequestedEventPayload payload) {
    List<Driver> availableDrivers = driverRepository.findByAvailableTrue();

    Optional<Driver> matched = matchingStrategy.findBestDriver(
            availableDrivers, payload.getPickupLat(), payload.getPickupLng());

    if (matched.isEmpty()) {
        driverEventPublisher.publishNoDriverAvailable(
                NoDriverAvailableEvent.of(payload.getRideId(), "No available drivers in the system")
        );
        return;
    }

    Driver driver = matched.get();
    driver.setAvailable(false);
    driverRepository.save(driver);

    driverEventPublisher.publishDriverAssigned(
            DriverAssignedEvent.of(payload.getRideId(), driver.getDriverId(),
                    driver.getVehicleNumber(), 10)
    );
}
```

**This is the core business flow.** Let's trace it step by step:

1. Load all available drivers from DB into memory (JVM heap)
2. Delegate to `matchingStrategy.findBestDriver()` — the Strategy pattern in action
3. If no match: publish `NO_DRIVER_AVAILABLE` event and return early
4. If matched: mark driver as unavailable, save to DB, publish `DRIVER_ASSIGNED` event

**Concurrency Risk:**  
Between step 2 (finding the best driver) and step 4 (marking them unavailable), another concurrent webhook call could select the same driver. Both calls would mark the same driver unavailable and both would publish `DRIVER_ASSIGNED` with the same `driverId`. This is a **race condition**.

**Solutions:**
- **Optimistic Locking:** Add `@Version` field to `Driver`. The second save would fail with `OptimisticLockException`.
- **Pessimistic Locking:** `SELECT FOR UPDATE` via `@Lock(LockModeType.PESSIMISTIC_WRITE)` in the repository query.
- **Atomic update:** `UPDATE drivers SET available = false WHERE driver_id = ? AND available = true` with a return check.

**ETA = 10 (hardcoded):**  
The `eta` passed to `DriverAssignedEvent.of()` is hardcoded to `10` minutes. This is clearly a placeholder. A production system would calculate real ETA based on traffic, distance, and historical data.

---

#### `@Transactional(readOnly = true)` methods

```java
@Transactional(readOnly = true)
public DriverResponseDto getDriver(String driverId) { ... }
```

`readOnly = true` hints to the transaction manager that no writes will occur. Effects:
1. **Hibernate:** Disables dirty checking (no need to snapshot entities for change detection). Saves CPU.
2. **Database:** Some drivers (PostgreSQL with read replicas) can route the query to a read replica.
3. **Spring:** Sets the JDBC connection to `readOnly(true)`, which some connection pools use for optimization.

---

## 9. Event-Driven Layer

### 9.1 `RideRequestedEventPayload.java`

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RideRequestedEventPayload {
    private String rideId;
    private String riderId;
    private String pickupLocation;
    private String dropLocation;
    private double pickupLat;
    private double pickupLng;
    private String timestamp;
}
```

**`@JsonIgnoreProperties(ignoreUnknown = true)`**  
When Jackson deserializes the webhook payload, any JSON fields not mapped to a Java field are silently ignored (instead of throwing `UnrecognizedPropertyException`). This makes the consumer **forward-compatible**: if the Ride Service adds new fields to the event payload in a future version, the Driver Service won't break.

> **Interview Question:** "What's the risk of `@JsonIgnoreProperties(ignoreUnknown = true)`?"  
> **Answer:** Silently dropped fields could mask a contract mismatch. If a critical field name changed (e.g., `pickupLat` → `pickupLatitude`), the driver service would receive `0.0` instead of failing loudly. This is why schema registries (like Confluent Schema Registry for Avro/Protobuf) are valuable in production event-driven systems.

---

### 9.2 `DriverAssignedEvent.java`

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DriverAssignedEvent {
    private String eventType;
    private String rideId;
    private String driverId;
    private String vehicleNumber;
    private int eta;
    private String timestamp;

    public static DriverAssignedEvent of(String rideId, String driverId, String vehicleNumber, int eta) {
        return DriverAssignedEvent.builder()
                .eventType("DRIVER_ASSIGNED")
                .rideId(rideId)
                .driverId(driverId)
                .vehicleNumber(vehicleNumber)
                .eta(eta)
                .timestamp(Instant.now().toString())
                .build();
    }
}
```

**Static Factory Method Pattern:**  
`DriverAssignedEvent.of()` is a named constructor. It enforces:
- `eventType` is always `"DRIVER_ASSIGNED"` (cannot be forgotten)
- `timestamp` is always set to now (cannot be left null)
- The caller only specifies the variable parts (`rideId`, `driverId`, `vehicleNumber`, `eta`)

> **Why prefer static factory methods over constructors?**  
> 1. Named methods communicate intent (`of`, `from`, `create`)
> 2. Can return cached instances (though not done here)
> 3. Can return subtypes
> 4. Enforce invariants that constructors alone cannot

**`Instant.now().toString()`**  
Produces an ISO 8601 UTC string like `"2025-05-10T12:00:00.000Z"`. This is the correct format for event timestamps: timezone-explicit, machine-readable, globally unambiguous.

---

### 9.3 `NoDriverAvailableEvent.java`

Same pattern as `DriverAssignedEvent`. Symmetry in the event schema design ensures consumers have a consistent deserialization experience.

---

### 9.4 `DriverEventPublisher.java`

```java
@Component
public class DriverEventPublisher {

    private final AtroposPublisherClient atroposPublisherClient;
    private final Gson gson;
    private final PublishMode publishMode;

    public DriverEventPublisher(
            AtroposPublisherClient atroposPublisherClient,
            Gson gson,
            @Value("${atropos.publish.mode}") String publishModeString) {
        this.atroposPublisherClient = atroposPublisherClient;
        this.gson = gson;
        this.publishMode = PublishMode.valueOf(publishModeString.toUpperCase());
    }
    ...
}
```

**`@Value("${atropos.publish.mode}")`**  
Spring's `@Value` annotation injects a property value from `application.properties` (or environment variables, vault secrets, etc.) at bean construction time. The value `"KINESIS"` is resolved and converted via `PublishMode.valueOf("KINESIS")`.

> **Framework Magic:** `@Value` is processed by `AutowiredAnnotationBeanPostProcessor`. During bean creation, Spring inspects constructor parameters for `@Value` and resolves them from the `Environment` abstraction (which aggregates all property sources in precedence order: command-line args > environment variables > application.properties > defaults).

**Constructor injection of `@Value`:**  
Unlike field injection (`@Value` on a field), this approach:
1. Is explicit about what the bean needs at construction time
2. Allows the constructor to validate the value immediately (`PublishMode.valueOf()` will throw `IllegalArgumentException` on an invalid string — fast fail at startup)
3. Stores the resolved `PublishMode` enum, not a raw String — stronger typing

**`private void publishEvent(String objectId, String topic, Object payload)`**  
The private Template Method. Both `publishDriverAssigned` and `publishNoDriverAvailable` delegate here. This eliminates code duplication and ensures all events go through the same SDK interaction:
1. Build `PubSubEvent` (builder pattern in the SDK)
2. Call `atroposPublisherClient.publish(builder, publishMode)`
3. Await completion (`.toCompletableFuture().get()` — synchronous wait)
4. Check `PublishStatus`
5. Throw on failure

**`atroposPublisherClient.publish(...).toCompletableFuture().get()`**  
This converts an async `CompletionStage` to a synchronous call by blocking with `.get()`. This means the calling thread (a Tomcat worker thread handling the webhook) **blocks** until Atropos confirms the event is published.

> **Threading Implication:** Blocking within a synchronous Servlet request handler is generally acceptable. However, if Atropos is slow (network latency to Kinesis), the Tomcat thread is held for the duration, reducing throughput. An improvement: use async processing, fire-and-forget, or a local outbox pattern with a background publisher.

---

## 10. Provider Layer

### 10.1 `DriverProvider.java`

```java
@Component
public class DriverProvider implements ObjectProvider<String> {

    public static final String OBJECT_TYPE = "driver";

    @Override
    public CompletionStage<Optional<String>> getObject(JID jid, Realm realm, Long tenantID) {
        return CompletableFuture.completedFuture(Optional.of(OBJECT_TYPE));
    }
}
```

**Purpose:** Integrates with Zeta's Sandbox access control framework. When an API call annotated with `@SandboxAuthorizedSync` is made, the framework resolves the "object" being accessed by calling `getObject()` on the registered `ObjectProvider`.

**`CompletableFuture.completedFuture(Optional.of(OBJECT_TYPE))`**  
Returns an already-completed future (no async work). The value returned (`"driver"`) tells the framework the object type, which is then checked against the caller's permissions. This is effectively a no-op provider — it always authorizes the object type string without any dynamic lookup.

**`public static final String OBJECT_TYPE = "driver"`**  
The constant is `public` and `static` so it can be referenced in annotations:
```java
@SandboxAuthorizedSync(object = "1@" + DriverProvider.OBJECT_TYPE + ".ridebooking.app", ...)
```
Annotation values must be compile-time constants. String concatenation of `static final` String fields qualifies.

---

### 10.2–10.3 `UserProvider` and `RideProvider`

Identical pattern to `DriverProvider`, with `OBJECT_TYPE = "rider"` and `"ride"` respectively. These register the Rider service and Ride service object types with the Sandbox framework even though they are foreign domains — this allows the access control framework to understand cross-service authorization contexts.

---

## 11. Exception Handling Layer

### 11.1 `DriverNotFoundException.java`

```java
public class DriverNotFoundException extends ResponseStatusException {
    public DriverNotFoundException(String driverId) {
        super(HttpStatus.NOT_FOUND, "Driver not found with id: " + driverId);
    }
}
```

**`ResponseStatusException`**  
A Spring MVC exception that carries an HTTP status code. When thrown, Spring's `DefaultHandlerExceptionResolver` automatically produces the correct HTTP response. It is caught by `GlobalExceptionHandler.handleResponseStatus()`.

**Why extend `ResponseStatusException` instead of a generic `RuntimeException`?**  
1. Carries the HTTP status code in the exception itself — no need to map it in the handler
2. Propagates cleanly through the Spring MVC layer
3. The `GlobalExceptionHandler` can handle all `ResponseStatusException` subclasses uniformly

**Why a custom class at all?**  
- Semantic clarity: `throw new DriverNotFoundException(id)` is more readable than `throw new ResponseStatusException(HttpStatus.NOT_FOUND, "...")`
- Can add domain context (e.g., which ID was not found)
- Can be targeted in tests: `assertThrows(DriverNotFoundException.class, ...)`

---

### 11.2 `GlobalExceptionHandler.java`

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) { ... }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) { ... }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoResourceFoundException ex) { ... }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) { ... }
}
```

**`@ControllerAdvice`**  
A meta-annotation combining `@Component`, `@ResponseBody`, and AOP advice. It declares a bean that is applied globally across **all** `@Controller`-annotated beans. Internally, Spring registers this as a global `HandlerExceptionResolver`.

> **Framework Magic:** When an exception propagates out of a controller method, `DispatcherServlet` invokes `HandlerExceptionResolverComposite`, which iterates registered resolvers. `ExceptionHandlerExceptionResolver` finds the matching `@ExceptionHandler` method via reflection and invokes it.

**Handler Priority — Most Specific to Most Generic:**  
1. `ResponseStatusException` — catches `DriverNotFoundException` (a subclass) and all other HTTP-status exceptions
2. `MethodArgumentNotValidException` — specifically catches Bean Validation failures from `@Valid`
3. `NoResourceFoundException` — catches 404s for static resources and unmapped URLs
4. `Exception` — catch-all for any unexpected error (500)

> **Production Readiness:** The catch-all handler returns a generic `"An unexpected error occurred"` message — correct. Leaking internal exception messages (stack traces, SQL errors, etc.) in API responses is an OWASP security vulnerability (A05: Security Misconfiguration).

**Error response structure:**
```json
{
    "timestamp": "2025-05-10T12:00:00",
    "status": 404,
    "message": "Driver not found with id: abc-123"
}
```
Consistent structure enables clients to parse errors reliably. A production improvement: add an `errorCode` field for machine-readable error categorization.

**`handleValidation` — Bean Validation Errors:**
```java
ex.getBindingResult().getFieldErrors()
    .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
```
Iterates all field-level validation failures and returns them as a map: `{"phone": "Phone is required", "name": "Name is required"}`. This is the correct approach — return all validation errors at once rather than failing on the first one (fail-fast for the user).

---

## 12. Controller Layer

### 12.1 `HealthController.java`

```java
@RestController
public class HealthController {
    @GetMapping("/health")
    public String healthCheck() {
        return "OK";
    }
}
```

**Purpose:** Minimal liveness probe endpoint. Returns `"OK"` with HTTP 200 if the application is running.

**`@RestController`**  
Combines `@Controller` + `@ResponseBody`. Every method return value is written directly to the HTTP response body (serialized by `HttpMessageConverter`) rather than interpreted as a view name.

**Why keep this separate from Spring Actuator's `/actuator/health`?**  
Actuator's health endpoint aggregates component health (DB connectivity, disk space, custom health indicators) and can return `503 Service Unavailable` if a dependency is unhealthy. This lightweight `/health` endpoint returns `200 OK` as long as the JVM is alive — useful as a Kubernetes **liveness** probe (is the app alive?) while Actuator serves as the **readiness** probe (is the app ready to receive traffic?).

The `helm/values.yaml` confirms this architecture:
```yaml
healthCheckPath: /actuator/health  # readiness
livenessProbeInitialDelaySeconds: 60
readinessProbeInitialDelaySeconds: 60
```

---

### 12.2 `DriverController.java`

```java
@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
@Tag(name = "Driver API", description = "Manage drivers, availability, location, and ride assignment")
public class DriverController {

    private static final SpectraLogger logger = OlympusSpectra.getLogger(DriverController.class);

    private final DriverService driverService;
    private final ObjectMapper objectMapper;
    ...
}
```

**`@RequestMapping("/api/drivers")`**  
Sets the base path for all endpoints in this controller. The prefix `/api/` signals this is an API (not a UI page). The path `/drivers` identifies the resource. This is RESTful resource naming — noun, plural.

**`ObjectMapper` injection:**  
Jackson's `ObjectMapper` is auto-configured by Spring Boot as a singleton bean. Injected here for manual JSON parsing in the webhook handler. `ObjectMapper` is **thread-safe** when shared — fields configured at construction time are immutable during request handling.

---

#### `POST /api/drivers` — Register Driver

```java
@PostMapping
@Operation(summary = "Register a new driver")
@SandboxAuthorizedSync(action = "driver.create", object = "1@driver.ridebooking.app", tenantID = "1001034")
public ResponseEntity<DriverResponseDto> registerDriver(@Valid @RequestBody DriverRegisterDto dto) {
    return ResponseEntity.status(HttpStatus.CREATED).body(driverService.registerDriver(dto));
}
```

**Full Request Lifecycle:**
1. HTTP POST arrives at Tomcat connector
2. `DispatcherServlet` receives the request
3. `RequestMappingHandlerMapping` matches to `registerDriver` based on path and HTTP method
4. `SandboxAuthorizedSync` AOP interceptor runs — verifies caller authorization
5. `@Valid` triggers Bean Validation on the deserialized `DriverRegisterDto`
   - `@RequestBody` triggers Jackson deserialization of the JSON body to `DriverRegisterDto`
   - If validation fails → `MethodArgumentNotValidException` → `GlobalExceptionHandler` → `400 Bad Request`
6. `driverService.registerDriver(dto)` is called (within a transaction)
7. The returned `DriverResponseDto` is serialized by Jackson to JSON
8. `ResponseEntity.status(HttpStatus.CREATED).body(...)` sets HTTP 201 status
9. Response is written back through the Servlet stack

**HTTP Semantics:**  
- `POST` is correct for resource creation (not idempotent).
- `201 Created` is correct (not `200 OK`). Some APIs also include a `Location` header pointing to the new resource URL (e.g., `/api/drivers/{newId}`). This service omits it but could add it.

**Idempotency Discussion:**  
`POST` is not idempotent. Retrying a failed POST may create duplicate drivers. The DB unique constraint on `phone` and `vehicleNumber` prevents exact duplicates, but a retry that times out mid-insert could leave partial state. Production systems often add an **idempotency key** header (e.g., `Idempotency-Key: <UUID>`) to deduplicate retries.

---

#### `GET /api/drivers/{driverId}` — Get Driver

```java
@GetMapping("/{driverId}")
@SandboxAuthorizedSync(action = "driver.view", ...)
public ResponseEntity<DriverResponseDto> getDriver(@PathVariable String driverId) {
    return ResponseEntity.ok(driverService.getDriver(driverId));
}
```

**`@PathVariable`**  
Extracts the `{driverId}` segment from the URL path. Spring's `ConversionService` can type-convert to the declared parameter type. Here it's `String` — no conversion needed.

**`ResponseEntity.ok(body)`**  
Shorthand for `ResponseEntity.status(200).body(body)`. Idiomatic Spring.

**HTTP Semantics:** `GET` is safe (no side effects) and idempotent. Repeated calls return the same result (or an updated snapshot). No request body (standard RESTful convention).

---

#### `PATCH /api/drivers/{driverId}/availability` — Update Availability

```java
@PatchMapping("/{driverId}/availability")
public ResponseEntity<DriverResponseDto> updateAvailability(
        @PathVariable String driverId,
        @RequestBody Map<String, Boolean> body) {
    boolean available = body.getOrDefault("available", true);
    return ResponseEntity.ok(driverService.updateAvailability(driverId, available));
}
```

**`PATCH` vs `PUT`:**  
`PATCH` is correct here — only one field is being changed, not the entire resource. `PUT` would require sending the complete driver representation.

**`Map<String, Boolean> body`:**  
A flexible but untyped approach. The consumer sends `{"available": false}`. Using a simple `Map` avoids creating a dedicated DTO for a single boolean field. However, this sacrifices type safety and documentation (Swagger cannot enumerate valid keys). A stricter alternative: a dedicated `AvailabilityUpdateDto` with `@NotNull Boolean available`.

**`body.getOrDefault("available", true)`:**  
If the `available` key is missing from the request body, defaults to `true`. This is a silent default that could be surprising. A better design: validate the key is present and throw 400 if missing.

---

#### `PATCH /api/drivers/{driverId}/location` — Update Location

```java
@PatchMapping("/{driverId}/location")
public ResponseEntity<DriverResponseDto> updateLocation(
        @PathVariable String driverId,
        @RequestBody LocationUpdateDto dto) {
    return ResponseEntity.ok(driverService.updateLocation(driverId, dto));
}
```

**Real-time Location Updates:**  
In production, GPS location is updated frequently (every few seconds). A REST POST per update generates enormous write load. Production-grade alternatives:
- WebSocket: persistent connection, server-side push
- MQTT: lightweight IoT protocol designed for high-frequency telemetry
- Kafka producer in the driver app: direct event streaming

---

#### `POST /api/drivers/events/ride-requested/webhook` — Webhook Handler

```java
@Hidden
@PostMapping("/events/ride-requested/webhook")
public ResponseEntity<String> handleRideRequestedWebhook(@RequestBody String rawPayload) {
    logger.info("[DriverController] Webhook received — RIDE_REQUESTED").log();
    try {
        RideRequestedEventPayload payload = objectMapper.readValue(rawPayload, RideRequestedEventPayload.class);
        driverService.handleRideRequested(payload);
        return ResponseEntity.ok("Processed");
    } catch (Exception e) {
        logger.error("[DriverController] Failed to process RIDE_REQUESTED webhook", e).log();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing failed");
    }
}
```

**`@Hidden`**  
A SpringDoc (Swagger) annotation that hides this endpoint from the generated API docs. Why? The webhook is an internal callback endpoint — not intended for external consumers. Hiding it from Swagger prevents accidental exposure and misuse.

**`@RequestBody String rawPayload`**  
Receives the raw JSON string instead of deserializing directly to `RideRequestedEventPayload`. This is a defensive pattern: if Atropos sends a malformed payload, the raw string can be logged for debugging before deserialization is attempted.

**Manual deserialization via `ObjectMapper`:**  
```java
RideRequestedEventPayload payload = objectMapper.readValue(rawPayload, RideRequestedEventPayload.class);
```
Manually calls Jackson's deserialization. Wrapped in try-catch so that malformed JSON (Jackson's `JsonProcessingException`) is caught and returns `500` rather than propagating to `GlobalExceptionHandler` (which would return a less informative response).

**`return ResponseEntity.ok("Processed")`**  
Returning `200` promptly acknowledges receipt to Atropos. If the response is delayed (because `handleRideRequested` is slow), Atropos might time out and retry, causing duplicate processing. This is why async handling is ideal.

---

#### `GET /api/drivers` — Get Available Drivers

```java
@GetMapping
public ResponseEntity<List<DriverResponseDto>> getAvailableDrivers() {
    return ResponseEntity.ok(driverService.getAvailableDrivers());
}
```

Fetches drivers where `available = true`. Calls `findByAvailableTrue()` → generates optimized SQL → streams results into a `List<DriverResponseDto>`.

**Pagination Missing:**  
No `Pageable` parameter. For large fleets, returning all available drivers in one response is not scalable. Should use `findByAvailableTrue(Pageable pageable)` with `Page<Driver>` result and return the page metadata alongside the data.

---

## 13. Database Migration

### `V1.0.1__driver_tables.sql` (Flyway)

```sql
CREATE TABLE IF NOT EXISTS drivers (
    driver_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT NOT NULL UNIQUE,
    vehicle_number TEXT NOT NULL UNIQUE,
    vehicle_type TEXT NOT NULL,
    latitude DOUBLE PRECISION DEFAULT 0.0,
    longitude DOUBLE PRECISION DEFAULT 0.0,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    rating DOUBLE PRECISION DEFAULT 5.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Flyway** is a database migration tool. It:
1. Creates a `flyway_schema_history` table to track applied migrations
2. Scans `classpath:db/migration` for files named `V{version}__{description}.sql`
3. Applies unapplied migrations in version order at application startup

**`V1.0.1__` prefix:**  
Version `1.0.1`. Flyway requires strict versioning. Once applied, this script is **never modified** — a new change requires a new migration file (e.g., `V1.0.2__add_index_on_available.sql`).

**`CREATE TABLE IF NOT EXISTS`:**  
Idempotent — won't fail if the table exists. Useful when `spring.jpa.hibernate.ddl-auto=update` is also set (Hibernate may create the table first). However, having both Flyway and `ddl-auto=update` is an anti-pattern. In production, `ddl-auto=validate` (or `none`) should be used alongside Flyway.

**`driver_id TEXT PRIMARY KEY`:**  
`TEXT` in PostgreSQL is equivalent to `VARCHAR` with no length limit. Appropriate for UUIDs. A fixed-length `CHAR(36)` or `UUID` native type would be marginally more storage-efficient.

**Column alignment with Entity:**

| SQL Column | Java Field | Type Match |
|---|---|---|
| `driver_id TEXT PK` | `String driverId @Id` | ✓ |
| `name TEXT NOT NULL` | `String name @Column(nullable=false)` | ✓ |
| `phone TEXT UNIQUE NOT NULL` | `String phone @Column(unique=true, nullable=false)` | ✓ |
| `available BOOLEAN NOT NULL` | `boolean available @Column(nullable=false)` | ✓ |
| `rating DOUBLE PRECISION` | `double rating` | ✓ |
| `created_at TIMESTAMP` | `LocalDateTime createdAt @Column(updatable=false)` | ✓ |

---

## 14. Test Suite

### 14.1 `DriverControllerTest.java`

```java
@ExtendWith(MockitoExtension.class)
class DriverControllerTest {

    @Mock
    private DriverService driverService;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @InjectMocks
    private DriverController driverController;
    ...
}
```

**`@ExtendWith(MockitoExtension.class)`**  
Registers Mockito as a JUnit 5 extension. The extension processes `@Mock`, `@Spy`, `@Captor`, and `@InjectMocks` annotations before each test.

**`@Mock`**  
Creates a Mockito mock of the annotated type. A mock is a dynamically generated class (via `ByteBuddy` or `Objenesis` + `CGLIB`) that implements all methods with default no-op behavior:
- Methods returning objects return `null`
- Methods returning primitives return default values (`false`, `0`, etc.)
- Methods are stubbed via `when(mock.method()).thenReturn(value)`

**`@InjectMocks`**  
Creates an instance of `DriverController` and injects the mocks into it. Mockito tries constructor injection first (matches `@RequiredArgsConstructor`'s generated constructor). This means tests run without Spring context — pure unit tests, fast and isolated.

> **What's NOT tested here:** HTTP serialization, validation (`@Valid`), and Spring MVC routing. Those require `@WebMvcTest` with a mock `MockMvc`. This test purely validates **controller logic** (HTTP status mapping, method delegation to service).

---

#### Test: `registerDriver_shouldReturnCreatedStatus`

```java
@Test
void registerDriver_shouldReturnCreatedStatus() {
    DriverRegisterDto dto = DriverRegisterDto.builder()...build();
    when(driverService.registerDriver(any(DriverRegisterDto.class))).thenReturn(sampleResponse());

    ResponseEntity<DriverResponseDto> result = driverController.registerDriver(dto);

    assertEquals(HttpStatus.CREATED, result.getStatusCode());
    assertNotNull(result.getBody());
    assertEquals("driver-001", result.getBody().getDriverId());
}
```

**`when(...).thenReturn(...)`:** Stubs the mock. When `registerDriver` is called with any `DriverRegisterDto`, return `sampleResponse()`. The actual `DriverService` bean is never invoked.

**`any(DriverRegisterDto.class)`:** An argument matcher that matches any non-null `DriverRegisterDto` instance.

**What this test validates:**
- The controller returns HTTP 201 Created (not 200 OK)
- The body is not null
- The body contains the expected driver ID

**What this test does NOT validate:**
- Validation constraints (`@NotBlank`)
- Duplicate registration conflict (409)
- DB interaction

---

#### Test: `handleRideRequestedWebhook_shouldReturnOkOnValidPayload`

```java
when(objectMapper.readValue(rawPayload, RideRequestedEventPayload.class))
        .thenReturn(payload);

ResponseEntity<String> result = driverController.handleRideRequestedWebhook(rawPayload);

assertEquals(HttpStatus.OK, result.getStatusCode());
verify(driverService).handleRideRequested(payload);
```

**`verify(driverService).handleRideRequested(payload)`:**  
Mockito verifies that `handleRideRequested` was called exactly once with the `payload` argument. This is interaction-based testing — verifies the controller correctly delegates to the service.

**Edge case not tested:** What happens when `objectMapper.readValue()` throws? The catch block should return 500 — but there's no test for this failure path. A robust test suite would have:
```java
when(objectMapper.readValue(rawPayload, ...)).thenThrow(new JsonProcessingException("bad json") {});
assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
```

---

### 14.2 `SandboxProviderTest.java`

```java
@Test
void driverProvider_getObject_returnsDriverType() {
    DriverProvider provider = new DriverProvider();
    CompletionStage<Optional<String>> result = provider.getObject(null, null, 0L);
    Optional<String> value = result.toCompletableFuture().join();
    assertTrue(value.isPresent());
    assertEquals(DriverProvider.OBJECT_TYPE, value.get());
}
```

**Tests the async provider contract:**  
`getObject()` returns a `CompletionStage`. The test calls `.toCompletableFuture().join()` to block and get the result — correct approach for testing async code synchronously.

**`assertSame(sacp, result)`:**  
In `sandboxConfig_registersAllObjectProviders`, verifies that the configuration bean returns the **same** `sacp` instance (not a new one). Uses `assertSame` (reference equality `==`) instead of `assertEquals` (value equality `.equals()`).

---

## 15. Design Patterns Catalogue

### Pattern 1: Strategy — Driver Matching

| Attribute | Detail |
|---|---|
| **Files** | `DriverMatchingStrategy.java` (interface), `NearestDriverMatchingStrategy.java` (concrete), `DriverService.java` (context) |
| **Why it qualifies** | The algorithm (driver selection) is encapsulated behind an interface and injected as a dependency |
| **Real-world analogy** | GPS navigation app letting you choose "fastest route", "shortest route", or "avoid highways" — same goal, different algorithm |
| **Advantage** | Add `SurgePricingMatchingStrategy` or `ZoneBasedMatchingStrategy` without touching `DriverService` |
| **Tradeoff** | Slight indirection cost; Spring must wire the correct implementation |
| **Interview talking point** | "We chose Strategy over if-else chains because it's open for extension and closed for modification — the Open/Closed Principle" |

---

### Pattern 2: Builder — DTOs and Events

| Attribute | Detail |
|---|---|
| **Files** | `DriverRegisterDto`, `DriverResponseDto`, `DriverAssignedEvent`, `NoDriverAvailableEvent`, `Driver` entity |
| **Why it qualifies** | `@Builder` generates a static nested `Builder` class with fluent setters and a terminal `build()` method |
| **Real-world analogy** | Ordering a custom sandwich — you specify each ingredient separately and confirm at the end |
| **Advantage** | Readable, avoids telescoping constructors, named parameters |
| **Tradeoff** | Slightly more generated code; immutability not guaranteed (setters still exist) |

---

### Pattern 3: Static Factory Method

| Attribute | Detail |
|---|---|
| **Files** | `DriverAssignedEvent.of()`, `NoDriverAvailableEvent.of()` |
| **Why it qualifies** | Named static method that constructs the object and enforces invariants (eventType, timestamp always set) |
| **Advantage** | Enforces mandatory fields; more readable than naked constructors |
| **Interview talking point** | "This follows Effective Java Item 1 — consider static factory methods instead of constructors" |

---

### Pattern 4: Repository

| Attribute | Detail |
|---|---|
| **Files** | `DriverRepository.java`, used in `DriverService.java` |
| **Why it qualifies** | Mediates between the domain and data mapping layers. Service talks to a repository abstraction, not SQL |
| **Real-world analogy** | A library catalog — you ask for books by criteria, you don't directly search the shelves |
| **Advantage** | Database-agnostic service layer; easy to mock in tests |
| **Spring magic** | `SimpleJpaRepository` is the actual implementation, generated at runtime via Spring Data |

---

### Pattern 5: DTO (Data Transfer Object)

| Attribute | Detail |
|---|---|
| **Files** | `DriverRegisterDto`, `DriverResponseDto`, `LocationUpdateDto` |
| **Why it qualifies** | Separate objects for transferring data across process boundaries (API) vs. the domain entity |
| **Advantage** | API contract decoupled from DB schema; security control over exposed fields |
| **CQRS connection** | Input DTOs (commands) and output DTOs (queries/responses) loosely mirror the Command/Query Responsibility Segregation principle |

---

### Pattern 6: Dependency Injection (IoC)

| Attribute | Detail |
|---|---|
| **Files** | Every class with `@RequiredArgsConstructor` or explicit constructors |
| **Why it qualifies** | Objects don't create their own dependencies; the IoC container provides them |
| **Real-world analogy** | A restaurant doesn't grow its own vegetables — it receives them from a supplier |
| **Advantage** | Testability, loose coupling, centralized lifecycle management |
| **Spring mechanism** | `BeanFactory` → `ApplicationContext` → `DefaultListableBeanFactory` instantiates beans using the constructor and injects dependencies |

---

### Pattern 7: Template Method

| Attribute | Detail |
|---|---|
| **Files** | `DriverEventPublisher.publishEvent()` (private), called by `publishDriverAssigned()` and `publishNoDriverAvailable()` |
| **Why it qualifies** | The skeleton algorithm (build → publish → check → throw) is fixed; only the topic name and payload vary (the variable "steps") |
| **Real-world analogy** | Making different types of coffee — the process is the same (grind → heat water → brew → pour); only the beans vary |
| **Advantage** | Eliminates code duplication; ensures all events pass through the same error handling |

---

### Pattern 8: Observer / Event-Driven

| Attribute | Detail |
|---|---|
| **Files** | `DriverEventPublisher`, `DriverAssignedEvent`, `NoDriverAvailableEvent`, webhook endpoint |
| **Why it qualifies** | The Driver Service publishes events (observing the ride request) and other services subscribe to the events it produces |
| **Real-world analogy** | A news agency — when news happens (event), subscribers (consumers) receive updates without the agency knowing who they are |
| **Advantage** | Loose coupling between services; services don't need to know each other's APIs |
| **Atropos role** | Atropos is the event broker (equivalent to Kafka/EventBridge) that routes events between producers and consumers |

---

### Pattern 9: Facade

| Attribute | Detail |
|---|---|
| **Files** | `DriverService` — provides a simplified interface over the complex interactions with repository, event publisher, and matching strategy |
| **Why it qualifies** | Controller calls one method; `DriverService` orchestrates multiple subsystems |
| **Real-world analogy** | A travel agent — you say "I want to go to Paris" and they handle flights, hotels, transfers |

---

### Pattern 10: Singleton

| Attribute | Detail |
|---|---|
| **Files** | All Spring beans (default scope) |
| **Why it qualifies** | Spring's IoC container maintains exactly one instance of each bean per context |
| **Thread Safety** | All beans must be thread-safe since they're shared across all request threads |
| **Implication** | Mutable state in beans is dangerous — instance fields should be either immutable or properly synchronized |

---

### Pattern 11: MVC (Model-View-Controller)

| Attribute | Detail |
|---|---|
| **Files** | Controller (`DriverController`) / Service+Entity (Model) / JSON response (View) |
| **Why it qualifies** | Spring MVC implements the MVC pattern at the web layer |
| **In REST APIs** | The "View" is the JSON serialization layer (Jackson), not a template engine |

---

### Pattern 12: Proxy

| Attribute | Detail |
|---|---|
| **Where** | Spring creates CGLIB proxies for `@Transactional` beans (`DriverService`), `@Configuration` beans (`AtroposConfig`), and `@ControllerAdvice` integration |
| **JDK Proxy vs CGLIB** | JDK proxy: only works for interface-based beans. CGLIB: subclasses the target class; used by Spring for non-interface beans |
| **Impact** | The injected `DriverService` reference is actually a proxy. Method calls go through the proxy first |

---

## 16. Runtime Request Flow (End-to-End)

### Flow: POST /api/drivers (Register Driver)

```
Client
  │
  │  POST /api/drivers
  │  Body: {"name":"Ravi","phone":"9876543210",...}
  ↓
Tomcat NIO Connector (port 8080)
  │  Accepts TCP connection
  │  Reads HTTP headers + body from socket
  │  Dispatches to a Tomcat worker thread
  ↓
HttpServlet (Tomcat → Servlet bridge)
  ↓
DispatcherServlet (Spring MVC's Front Controller)
  │  Receives HttpServletRequest/HttpServletResponse
  │  Calls HandlerMapping
  ↓
RequestMappingHandlerMapping
  │  Looks up registered handler methods
  │  Matches: POST /api/drivers → DriverController.registerDriver()
  │  Returns HandlerExecutionChain (handler + interceptors)
  ↓
HandlerInterceptors (pre-handle)
  │  SandboxAuthorizedSync AOP interceptor
  │  → Checks caller authorization via SandboxAccessControlProvider
  │  → If unauthorized: throw 403
  ↓
RequestMappingHandlerAdapter
  │  Resolves method arguments:
  │    @RequestBody DriverRegisterDto → Jackson reads body JSON
  │    → ObjectMapper.readValue(body, DriverRegisterDto.class)
  │    → @Valid triggers LocalValidatorFactoryBean
  │       → hibernate-validator validates @NotBlank fields
  │       → Failure: throws MethodArgumentNotValidException → 400
  ↓
DriverController.registerDriver(dto)
  │
  ↓
DriverService (CGLIB proxy)
  │  TransactionInterceptor.invoke()
  │    → PlatformTransactionManager.getTransaction()
  │    → HikariCP connection pool: getConnection()
  │    → connection.setAutoCommit(false)
  │    → SAVEPOINT created
  │
  ↓ (real DriverService)
  registerDriver(dto)
  │  existsByPhone → Hibernate executes: SELECT COUNT(*) FROM drivers WHERE phone=?
  │  existsByVehicleNumber → SELECT COUNT(*) FROM drivers WHERE vehicle_number=?
  │  Driver.builder()...build()
  │  @PrePersist: UUID generated, createdAt set, rating=5.0, available=true
  │  driverRepository.save(driver)
  │    → EntityManager.persist(driver)
  │    → Hibernate generates INSERT SQL
  │    → JDBC PreparedStatement executed
  │  mapToResponse(driver)
  │
  ↓
TransactionInterceptor
  │  No exception: connection.commit()
  │  connection released back to HikariCP pool
  ↓
Jackson serialization
  │  ObjectMapper.writeValueAsString(DriverResponseDto)
  │  → JSON string written to HttpServletResponse output stream
  ↓
ResponseEntity builder sets HTTP 201 status
  ↓
Tomcat sends HTTP response to client
```

---

## 17. Hidden Framework Magic

### Auto-Configuration

Spring Boot's `@EnableAutoConfiguration` scans `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` in every JAR. Key auto-configurations triggered by this service's classpath:

| Auto-Config class | What it sets up |
|---|---|
| `DataSourceAutoConfiguration` | HikariCP connection pool using `spring.datasource.*` properties |
| `HibernateJpaAutoConfiguration` | `LocalContainerEntityManagerFactoryBean`, `JpaTransactionManager` |
| `JpaRepositoriesAutoConfiguration` | Scans for `JpaRepository` interfaces and creates proxy implementations |
| `WebMvcAutoConfiguration` | `DispatcherServlet`, `HandlerMapping`, Jackson `HttpMessageConverter` |
| `FlywayAutoConfiguration` | Runs SQL migrations at startup |
| `SpringDocAutoConfiguration` | Sets up Swagger UI and OpenAPI spec endpoint |
| `ActuatorAutoConfiguration` | Exposes `/actuator/health`, `/actuator/metrics` |

### Component Scanning

Spring performs a **depth-first scan** of the packages declared in `@ComponentScan`. For each class file:
1. Load via `ClassLoader`
2. Check for meta-annotations using reflection (`Class.getAnnotations()`)
3. If `@Component` (or stereotype) found: register as `BeanDefinition` in `DefaultListableBeanFactory`
4. After all beans are defined: instantiate in dependency order

### Bean Lifecycle

```
BeanDefinition registered
  ↓
BeanFactoryPostProcessor (e.g., PropertySourcesPlaceholderConfigurer resolves @Value)
  ↓
Constructor called (dependency injection)
  ↓
BeanPostProcessor.postProcessBeforeInitialization (e.g., @Autowired field injection)
  ↓
@PostConstruct method called (if present)
  ↓
InitializingBean.afterPropertiesSet() (if implemented)
  ↓
Bean is ready (available in context)
  ↓
Context closed:
  ↓
@PreDestroy called (cleanup)
  ↓
DisposableBean.destroy() called (if implemented)
```

### CGLIB Proxying for `@Transactional`

1. At startup, `AbstractAutoProxyCreator` (a `BeanPostProcessor`) detects that `DriverService` has `@Transactional`.
2. It creates a CGLIB subclass of `DriverService` at runtime using bytecode generation.
3. The proxy overrides every public method: `registerDriver`, `updateAvailability`, etc.
4. When a method is called on the proxy, `CglibAopProxy.DynamicAdvisedInterceptor.intercept()` runs first.
5. The `TransactionInterceptor` is invoked, which starts/joins a transaction.
6. The real method on the delegate is called.
7. On return, the transaction is committed; on exception, rolled back.

### Hibernate Session Management

Hibernate's `Session` (≈ JPA `EntityManager`) has a **first-level cache** (identity map). Within a single transaction:
- The first `findById("abc")` hits the DB and stores the result in the session cache
- A subsequent `findById("abc")` in the same transaction returns the cached object — no DB hit
- This is why `driver.setAvailable(false); driverRepository.save(driver)` works correctly — the same `Driver` object is in the session

### Jackson Deserialization Magic

Jackson deserializes JSON to Java objects using:
1. `ObjectMapper` reads the JSON string
2. For each JSON field name, Jackson looks for:
   - A matching Java field (via reflection)
   - A setter method (`setName(String)`) — Lombok's `@Setter` generates these
3. `LocalDateTime` fields require `JavaTimeModule` (auto-configured by Spring Boot) for ISO 8601 parsing

---

## 18. Event-Driven Architecture Deep Dive

### Architecture Overview

```
Ride Service
   │
   │  RIDE_REQUESTED event
   │  (published to Atropos/Kinesis topic)
   ↓
Atropos Event Router
   │
   │  HTTP POST /api/drivers/events/ride-requested/webhook
   ↓
Driver Service
   │
   ├── Finds best driver (Strategy)
   │
   ├── If found:
   │     DRIVER_ASSIGNED event → Atropos → Ride Service
   │
   └── If not found:
         NO_DRIVER_AVAILABLE event → Atropos → Ride Service
```

### Eventual Consistency

The system is **eventually consistent**:
- After `DRIVER_ASSIGNED` is published, the Ride Service may still show "searching for driver" briefly
- The Driver Service DB is immediately updated (driver marked unavailable)
- The Ride Service DB is updated asynchronously when it processes `DRIVER_ASSIGNED`
- During the gap, the two services have inconsistent views — this is acceptable in distributed systems

### Idempotency & Retries

**Risk:** If the Driver Service processes a webhook, marks a driver unavailable, and then fails before publishing `DRIVER_ASSIGNED`, Atropos may retry the webhook. The driver is already marked unavailable — the second call finds no available driver and publishes `NO_DRIVER_AVAILABLE`. Incorrect behavior.

**Mitigation (Outbox Pattern):**  
Store the outbound event in the same DB transaction as the driver state change. A separate background process reads from the outbox table and publishes to Atropos. Guarantees exactly-once semantics.

### Dead-Letter Queue (DLQ)

If the webhook handler consistently returns 500, Atropos should route the event to a DLQ after N retries. The Driver Service should then have a way to inspect and replay DLQ events. Currently not implemented — a production gap.

### Async vs Sync Publishing

The current implementation uses `.toCompletableFuture().get()` — **synchronous blocking**. This means:
- The webhook HTTP response is delayed by Kinesis publish latency (typically 20-50ms, but can spike)
- The Tomcat thread is held during this wait
- Under heavy load, this can cause thread pool exhaustion

**Improvement:** Publish asynchronously and return 202 Accepted immediately. Use a background retry mechanism for publish failures.

---

## 19. Diagrams

### Request Flow Diagram

```
┌────────┐    POST /api/drivers     ┌─────────────────────┐
│ Client │ ───────────────────────► │  DispatcherServlet  │
└────────┘                          └──────────┬──────────┘
                                               │
                                    ┌──────────▼──────────┐
                                    │  HandlerMapping     │
                                    │ (find controller)   │
                                    └──────────┬──────────┘
                                               │
                                    ┌──────────▼──────────┐
                                    │  SandboxAuth AOP    │
                                    │  (authorization)    │
                                    └──────────┬──────────┘
                                               │
                                    ┌──────────▼──────────┐
                                    │  Bean Validation    │
                                    │  (@Valid)           │
                                    └──────────┬──────────┘
                                               │
                                    ┌──────────▼──────────┐
                                    │  DriverController   │
                                    └──────────┬──────────┘
                                               │
                                    ┌──────────▼──────────┐
                                    │  DriverService      │
                                    │  (CGLIB proxy)      │
                                    │  @Transactional     │
                                    └──────────┬──────────┘
                                               │
                                    ┌──────────▼──────────┐
                                    │  DriverRepository   │
                                    │  (JPA proxy)        │
                                    └──────────┬──────────┘
                                               │
                                    ┌──────────▼──────────┐
                                    │  PostgreSQL          │
                                    │  (HikariCP pool)    │
                                    └─────────────────────┘
```

---

### Service Dependency Diagram

```
DriverController
    ├── DriverService
    │       ├── DriverRepository ──────── PostgreSQL
    │       ├── DriverMatchingStrategy
    │       │       └── NearestDriverMatchingStrategy
    │       └── DriverEventPublisher
    │               ├── AtroposPublisherClient ── Kinesis
    │               └── Gson
    └── ObjectMapper (Jackson)

SandboxConfig
    ├── UserProvider
    ├── DriverProvider
    └── RideProvider
        └── SandboxAccessControlProvider (Zeta SDK)
```

---

### Entity Relation Diagram

```
┌────────────────────────────────────────┐
│               drivers                  │
├────────────────────────────────────────┤
│ PK  driver_id     TEXT                 │
│     name          TEXT NOT NULL        │
│     phone         TEXT NOT NULL UNIQUE │
│     vehicle_number TEXT NOT NULL UNIQUE│
│     vehicle_type  TEXT NOT NULL        │
│     latitude      DOUBLE PRECISION     │
│     longitude     DOUBLE PRECISION     │
│     available     BOOLEAN NOT NULL     │
│     rating        DOUBLE PRECISION     │
│     created_at    TIMESTAMP            │
└────────────────────────────────────────┘

No foreign keys in this service.
Driver ownership is self-contained.
```

---

### Event Flow Diagram

```
┌───────────────┐         ┌─────────────┐         ┌─────────────────┐
│  Ride Service │         │   Atropos   │         │  Driver Service │
│               │         │  (Kinesis)  │         │                 │
│               │ PUBLISH │             │ WEBHOOK  │                 │
│  RIDE_REQUEST │────────►│ ride-request│─────────►│ /events/ride-   │
│   (event)     │         │   ed topic  │          │  requested/     │
│               │         │             │          │  webhook        │
│               │         │             │          │       │         │
│               │         │             │          │  Find best      │
│               │         │             │          │  driver         │
│               │         │             │          │       │         │
│               │◄────────│◄────────────│◄─────────│  PUBLISH        │
│  DRIVER_      │ DELIVER │driver-      │  PUBLISH │  DRIVER_        │
│  ASSIGNED     │         │assigned     │          │  ASSIGNED       │
│               │         │  topic      │          │                 │
└───────────────┘         └─────────────┘         └─────────────────┘
```

---

### Sequence Diagram: Ride Matching Flow

```
Atropos         DriverController    DriverService       DriverRepository    Strategy       Publisher
   │                  │                  │                    │               │               │
   │  POST webhook    │                  │                    │               │               │
   │─────────────────►│                  │                    │               │               │
   │                  │ handleRideReq()  │                    │               │               │
   │                  │─────────────────►│                    │               │               │
   │                  │                  │ findByAvailableTrue│               │               │
   │                  │                  │───────────────────►│               │               │
   │                  │                  │   List<Driver>     │               │               │
   │                  │                  │◄───────────────────│               │               │
   │                  │                  │                    │findBestDriver │               │
   │                  │                  │──────────────────────────────────►│               │
   │                  │                  │          Optional<Driver>          │               │
   │                  │                  │◄──────────────────────────────────│               │
   │                  │                  │ save(driver, available=false)      │               │
   │                  │                  │───────────────────►│               │               │
   │                  │                  │                    │               │publishDriverAssigned
   │                  │                  │─────────────────────────────────────────────────►│
   │                  │                  │                    │               │  → Kinesis   │
   │  200 OK          │                  │                    │               │               │
   │◄─────────────────│                  │                    │               │               │
```

---

## 20. Interview Deep Dive — Tough Questions

### Spring Internals

**Q: How does `@Transactional` work internally? What happens if you call a `@Transactional` method from within the same class?**

A: Spring wraps beans with `@Transactional` methods in a CGLIB proxy. When you inject `DriverService`, you get the proxy. The proxy intercepts each method call and runs `TransactionInterceptor`. If you call `this.someMethod()` from within the class, you bypass the proxy — the call goes directly to the real object, and no transaction management occurs. This is a well-known Spring gotcha. Solution: inject the bean into itself via `@Autowired DriverService self` (which gives you the proxy), or restructure to avoid self-invocation.

---

**Q: What is the difference between `BeanFactory` and `ApplicationContext`?**

A: `BeanFactory` is the base IoC container — lazy bean instantiation, basic DI. `ApplicationContext` extends it with: eager singleton instantiation, AOP support, event publishing (`ApplicationEventPublisher`), internationalization (`MessageSource`), environment abstraction (`Environment`), and `BeanPostProcessor` registration. Spring Boot always uses `ApplicationContext` (specifically `AnnotationConfigServletWebServerApplicationContext` for web apps).

---

**Q: Explain the difference between `@Component`, `@Service`, `@Repository`, and `@Controller`.**

A: All are specializations of `@Component` — all register beans via component scan. The differences:
- `@Repository`: adds exception translation (wraps `PersistenceException` into `DataAccessException`)
- `@Service`: semantic marker, may be targeted by AOP
- `@Controller`: marks MVC controllers, integrates with `DispatcherServlet`
- `@RestController`: `@Controller` + `@ResponseBody`

---

### JPA / Hibernate Internals

**Q: What is the difference between `persist()` and `merge()` in JPA?**

A: `persist()` makes a _transient_ entity _managed_ — it must not exist in the DB. `merge()` handles both new (no ID) and existing (has ID) entities by copying the state. Spring Data's `save()` uses `entityInformation.isNew(entity)` to decide: if the entity has no ID, `persist()`; otherwise `merge()`. In this service, the UUID is generated in `@PrePersist` — which runs before `persist()` — so `entityInformation.isNew()` checks if the ID was null before `@PrePersist`. Spring Data uses `@Version` or `@Id` nullability to detect new entities.

---

**Q: What is the N+1 problem and how would you fix it here if `Driver` had a `List<Ride>` relation?**

A: With `FetchType.LAZY`, fetching 100 drivers and then accessing `driver.getRides()` for each would execute 1 + 100 = 101 SQL queries. Fixes:
1. `JOIN FETCH` in JPQL: `SELECT d FROM Driver d JOIN FETCH d.rides`
2. `@EntityGraph(attributePaths = {"rides"})` on the repository method
3. `@BatchSize(size = 50)` — Hibernate fetches rides in batches of 50

---

**Q: Explain the Hibernate first-level vs second-level cache.**

A: **First-level (L1):** Session-scoped identity map. Every entity fetched in a session is cached by ID. Automatic, cannot be disabled. **Second-level (L2):** SessionFactory-scoped, shared across sessions/requests. Requires explicit configuration (Ehcache, Infinispan, Redis). L2 cache is NOT configured in this service. For a high-read driver catalog (driver profiles rarely change), L2 caching for `Driver` entities would improve performance significantly.

---

### Distributed Systems

**Q: How would you handle the race condition in `handleRideRequested` where two concurrent requests could assign the same driver?**

A: 
1. **Optimistic locking:** Add `@Version Long version` to `Driver`. Hibernate includes `WHERE version = ?` in UPDATE. The second concurrent update fails with `OptimisticLockException`. Service catches and retries with the next best driver.
2. **Pessimistic locking:** Add `@Lock(LockModeType.PESSIMISTIC_WRITE)` to a custom repository query. Generates `SELECT ... FOR UPDATE` in PostgreSQL. Serializes concurrent access but holds DB locks.
3. **Atomic status update:** Single `UPDATE drivers SET available = false WHERE driver_id = ? AND available = true RETURNING driver_id` — if it returns a row, this instance "won" the assignment.

---

**Q: How would you scale this service to handle 100,000 concurrent ride requests?**

A:
1. **Horizontal scaling:** Run multiple instances behind a load balancer (already Kubernetes-ready via Helm chart)
2. **Connection pooling:** HikariCP already used; tune `maximum-pool-size` to match DB capacity
3. **Geographic sharding:** Route requests to the nearest datacenter instance; driver data sharded by region
4. **Cache available drivers:** Cache the `findByAvailableTrue()` result in Redis with TTL. Invalidate on availability change.
5. **Async assignment:** Accept the webhook, put it on an internal queue (Kafka), background workers process assignments and publish events
6. **PostGIS:** Replace in-memory Haversine with a DB-side geo-spatial query — eliminates loading all drivers into JVM heap

---

### Security (OWASP)

**Q: What security vulnerabilities exist in this service?**

A:
- **A01 - Broken Access Control:** `@SandboxAuthorizedSync` protects most endpoints. The webhook endpoint is unprotected — any caller can POST to it. A shared secret or webhook signature verification should be added.
- **A03 - Injection:** All DB queries use parameterized statements (JPA/Hibernate PreparedStatements). No raw string concatenation in SQL. Safe.
- **A04 - Insecure Design:** The TOCTOU race condition in driver registration. The hardcoded ETA of 10.
- **A05 - Security Misconfiguration:** `spring.jpa.show-sql=false` is correct (no SQL in logs). Error messages are generic (500 returns "unexpected error occurred").
- **A09 - Security Logging Failures:** Logging could be improved — PII (phone numbers) is currently logged in registration. Should be masked.

---

### Production Readiness

**Q: Is this service production-ready? What would you change?**

A:
1. **Observability:** SpectraLogger + Actuator are present. Would add distributed tracing (OpenTelemetry / Jaeger) to correlate the webhook call across services.
2. **Circuit breaker:** Calls to Atropos can fail. Would add Resilience4j circuit breaker around `publishEvent()`.
3. **Retry + DLQ:** Webhook processing failures need retry and dead-letter handling.
4. **Pagination:** All-drivers endpoints need pagination.
5. **GPS validation:** `latitude`/`longitude` fields need range validation (`-90 ≤ lat ≤ 90`, `-180 ≤ lng ≤ 180`).
6. **Health indicator:** Custom `HealthIndicator` for Atropos connectivity.
7. **Rate limiting:** Protect the location update endpoint from flooding.

---

## 21. How to Impress the Interviewer

### Advanced Implementation Details to Highlight

1. **Strategy Pattern for Driver Matching:**  
   "We designed driver matching as a pluggable Strategy. Tomorrow, if the business wants surge-pricing-aware matching or zone-based assignment, we implement a new `DriverMatchingStrategy` and swap it in — `DriverService` doesn't change at all. This is the Open/Closed Principle in action."

2. **Constructor Injection Over Field Injection:**  
   "Every dependency is injected via constructor, never `@Autowired` on fields. This makes every bean independently testable with plain `new` — no Spring context needed in unit tests. `@RequiredArgsConstructor` generates the boilerplate without noise."

3. **`@PrePersist` for Default Values:**  
   "Instead of setting defaults in the service layer (which could be bypassed), we put them in `@PrePersist`. The entity guarantees its own invariants — no driver can be persisted without a UUID, creation timestamp, and initial rating. This is Domain-Driven Design's _invariant protection_."

4. **`@Transactional(readOnly = true)` on Query Methods:**  
   "Read-only transactions disable Hibernate's dirty checking, reducing CPU overhead. On databases with read replicas, Spring can route read-only transactions to replicas automatically."

5. **Event-Driven Decoupling:**  
   "The Driver Service doesn't call the Ride Service's REST API directly. It publishes events. This means the Driver Service can operate even if the Ride Service is down. Temporal decoupling is one of the key benefits of event-driven architecture."

6. **`@JsonIgnoreProperties(ignoreUnknown = true)` on Inbound Events:**  
   "This makes the event consumer forward-compatible. If the Ride Service adds fields to `RIDE_REQUESTED` in version 2, the Driver Service processes it correctly without redeployment. This is the _Tolerant Reader_ pattern."

7. **Static Factory Methods on Events:**  
   "Events that use `DriverAssignedEvent.of(...)` make it impossible to forget `eventType` or `timestamp`. Static factory methods with meaningful names enforce invariants better than raw constructors."

8. **Explicit Swagger Documentation with `@Hidden`:**  
   "The webhook endpoint is `@Hidden` in Swagger because it's an internal callback, not a public API. Documenting internal endpoints in public Swagger creates a surface area for misuse."

9. **`countByAvailableTrue()` for Stats:**  
   "We return the count of available drivers in `getDriverStats()` using `COUNT(*)` — never loading all driver objects into memory just to count them. This is a common performance trap."

10. **Kubernetes Readiness Separation:**  
    "The service exposes two health endpoints: `/health` for Kubernetes liveness (is the process alive?) and `/actuator/health` for readiness (is the app ready to serve traffic, including DB connectivity?). This prevents Kubernetes from sending traffic to an instance before its database connection pool is established."

---

### Clean Code Principles in Use

| Principle | Evidence |
|---|---|
| **SRP** | Each class has one job: Repository for data, Service for logic, Controller for HTTP, Publisher for events |
| **OCP** | Strategy pattern allows adding matching algorithms without modifying DriverService |
| **LSP** | `NearestDriverMatchingStrategy` correctly substitutes for `DriverMatchingStrategy` |
| **ISP** | `DriverMatchingStrategy` has a single method — clients depend only on what they use |
| **DIP** | `DriverService` depends on the `DriverMatchingStrategy` interface, not the concrete class |
| **DRY** | `mapToResponse()` private helper. `publishEvent()` template method. `DRIVER_ID_ATTR` constant |
| **Fail-fast** | Bean Validation at the controller boundary. `@PrePersist` for entity defaults. `PublishMode.valueOf()` at startup |

---

## 22. Potential Improvements

### Scalability
1. **PostGIS geo-spatial queries:** Move Haversine computation from JVM to PostgreSQL. Eliminates loading all available drivers into memory.
   ```sql
   SELECT * FROM drivers
   WHERE available = true
     AND ST_Distance(
       ST_SetSRID(ST_Point(longitude, latitude), 4326)::geography,
       ST_SetSRID(ST_Point(:pickupLng, :pickupLat), 4326)::geography
     ) <= 5000  -- 5km in meters
   ORDER BY ST_Distance(...) ASC
   LIMIT 1;
   ```

2. **Pagination:** Add `Pageable` to all list endpoints to prevent OOM on large datasets.

3. **Redis caching for available drivers:** Cache the result of `findByAvailableTrue()` with a short TTL (5-10 seconds). Invalidate when any driver's availability changes.

4. **Async event publishing:** Publish events on a separate thread pool. Return the webhook response immediately. Use the **Outbox Pattern** for guaranteed delivery.

### Performance
5. **Partial index on availability:**
   ```sql
   CREATE INDEX idx_drivers_available_only ON drivers(driver_id, rating, created_at)
   WHERE available = true;
   ```

6. **Connection pool tuning:** Configure `spring.datasource.hikari.maximum-pool-size` based on PostgreSQL's `max_connections` and expected concurrency.

### Security
7. **Webhook signature verification:** Add HMAC-SHA256 signature validation on the webhook endpoint to prevent unauthorized event injection.

8. **Phone number masking in logs:** Never log full phone numbers. Use `phone.substring(0, 3) + "****" + phone.substring(7)`.

9. **GPS coordinate validation:** Add `@DecimalMin`/`@DecimalMax` constraints on latitude (-90 to 90) and longitude (-180 to 180).

10. **Rate limiting:** Apply on the `/location` endpoint (frequently called by driver apps). Use Bucket4j or Spring Cloud Gateway rate limiting.

### Code Quality
11. **MapStruct for DTO mapping:** Replace manual `mapToResponse()` with a MapStruct mapper for compile-time-safe, zero-reflection mapping.

12. **Flyway + `ddl-auto=validate`:** Replace `ddl-auto=update` with `validate` in production. Let Flyway own schema changes exclusively.

13. **Dedicated `AvailabilityUpdateDto`:** Replace `Map<String, Boolean>` with a typed DTO for the availability PATCH endpoint.

14. **`@DecimalMin`/`@DecimalMax` on ETA:** Make the ETA dynamic (distance/speed estimate) instead of hardcoded `10`.

15. **Optimistic locking:** Add `@Version Long version` to `Driver` entity to prevent lost-update race conditions.

16. **`Instant` instead of `LocalDateTime`:** Use `Instant` for `createdAt` to be timezone-agnostic in distributed deployments.

### Testing
17. **Service layer unit tests:** Currently, only controller and provider tests exist. Add `DriverServiceTest` with mocked repository and publisher.

18. **`@WebMvcTest` for controller:** Test actual HTTP serialization, `@Valid` validation, and error responses through the Spring MVC stack.

19. **Integration tests with Testcontainers:** Spin up a real PostgreSQL container for `@DataJpaTest`. Verify Flyway migrations and actual SQL execution.

20. **Test the failure path in `handleRideRequestedWebhook`:** The Jackson parse failure path (returning 500) is untested.

---

## 23. Deployment & Containerization

### Dockerfile

```dockerfile
FROM 813361731051.dkr.ecr.ap-south-1.amazonaws.com/zeta-openjdk:2.0.0

ARG app
ARG version
ARG lastCommitHash
ARG lastCommitAuthorEmail

ENV ARTIFACT_NAME $app
ENV ARTIFACT_VERSION $version
ENV ARTIFACT_COMMIT_ABR $lastCommitHash
ENV ARTIFACT_COMMITTER $lastCommitAuthorEmail

ENV APP $app
ENV JAR_PATH "$DATA_PATH/$APP.jar"

RUN mkdir -p "$LOGS_PATH/$APP" && chown -R $USERNAME:$USERNAME "$LOGS_PATH/$APP"
COPY --chown=$USERNAME:$USERNAME ./target/$APP.jar $JAR_PATH
USER $USERNAME:$USERNAME
```

**Base image from private ECR (`813361731051.dkr.ecr.ap-south-1.amazonaws.com`):**  
Zeta's internal OpenJDK image. Using a private, controlled base image is a security best practice — avoids supply chain attacks from public DockerHub images. The image presumably has security hardening (non-root user, minimal OS packages).

**`ARG` vs `ENV`:**  
`ARG` are build-time variables (passed via `--build-arg`). `ENV` are runtime environment variables. Build metadata (`version`, `lastCommitHash`) is captured in `ENV` for runtime introspection — useful for debugging which version is running in production.

**`COPY --chown=$USERNAME:$USERNAME ./target/$APP.jar $JAR_PATH`:**  
The compiled fat JAR (Spring Boot uber-JAR, all dependencies bundled) is copied into the container. `--chown` ensures the file is owned by the non-root user — principle of least privilege.

**`USER $USERNAME:$USERNAME`:**  
The container runs as a non-root user. This is an OWASP container security requirement — if the process is compromised, the attacker has minimal OS privileges.

**JAR naming:**  
The `pom.xml` sets `<finalName>zea-2026-b02-nischay-ride-booking-driver-service</finalName>`. The JAR is built as `target/zea-2026-b02-nischay-ride-booking-driver-service.jar`. The `APP` build arg matches this name.

---

### Helm Chart (`values.yaml`)

```yaml
hpa:
  enabled: false      # No autoscaling — manual replica count

replicaCount: 1       # Single instance (dev/staging)

resources:
  limits:
    memory: 2Gi
  requests:
    cpu: 1
    memory: 1Gi
```

**HPA disabled:**  
In staging/dev. Production would enable HPA with CPU/memory thresholds (e.g., scale up when CPU > 70%).

**Memory limits (2Gi limit, 1Gi request):**  
JVM heap is configured via `javaXms=128M, javaXmx=512M`. The container limit (2Gi) is higher to accommodate:
- JVM heap (512M)
- JVM off-heap (Metaspace, Code cache, NIO buffers)
- OS overhead

**Vault integration:**
```yaml
KINESIS_ACCESSKEY: vault:secrets/data/zone/common/logging#KINESIS_ACCESSKEY
```
AWS Kinesis credentials are injected from HashiCorp Vault at runtime. No secrets in the Docker image or source code — security best practice.

**ServiceMonitor for Prometheus:**
```yaml
serviceMonitor:
  enabled: true
  monitors:
    - path: /actuator/metrics
      interval: 60s
```
Prometheus scrapes metrics from Actuator's `/actuator/metrics` every 60 seconds. Enables dashboards (Grafana) and alerting (PagerDuty) based on JVM metrics, request counts, DB pool utilization, etc.

---

*This document was generated as senior staff engineer interview preparation material for the Driver Service microservice. All 19 source files have been analyzed line-by-line.*
