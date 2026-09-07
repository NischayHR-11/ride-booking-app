# 🚖 Ride Booking Platform — Complete Interview Demo Guide

> **Architecture:** 3 Spring Boot microservices communicating via **Atropos event bus** (Kinesis locally)  
> **Stack:** Java 17 · Spring Boot 3.5.7 · PostgreSQL 16 · Event-Driven Architecture · Haversine Matching · Simulated Pricing

---

## 📐 Architecture at a Glance

```
┌─────────────────────────────────────────────────────────────────┐
│                     POSTMAN (Your Interface)                     │
└─────────┬───────────────┬──────────────────────┬────────────────┘
          │               │                      │
          ▼               ▼                      ▼
┌─────────────────┐ ┌─────────────────┐ ┌──────────────────────┐
│  RIDE-SERVICE   │ │ DRIVER-SERVICE  │ │ NOTIFICATION-SERVICE │
│  :8080          │ │ :8081           │ │ :8082                │
│                 │ │                 │ │                      │
│ Riders + Rides  │ │ Drivers         │ │ Notifications        │
│ Estimated Cost  │ │ 5km Matching    │ │ Event Alerts         │
│ Lifecycle Mgmt  │ │ Haversine Algo  │ │ Read/Unread          │
└────────┬────────┘ └────────▲────────┘ └──────────▲───────────┘
         │                   │                      │
         │   RIDE_REQUESTED ─┘                      │
         │                                          │
         │ ◄────────── ATROPOS EVENT BUS ───────────┤
         │         (Kinesis / KINESIS mode)          │
         │                                          │
         │   DRIVER_ASSIGNED ──────────────────────►│
         │   RIDE_CANCELLED ───────────────────────►│
         │   RIDE_COMPLETED ───────────────────────►│
         │                                          │
         ▼                                          │
  ┌────────────┐                                    │
  │ PostgreSQL │◄───────────────────────────────────┘
  │  :5432     │
  │ride_booking│
  └────────────┘
```

---

## 🚀 Pre-Demo Setup (Run Once)

### 1. Start PostgreSQL
```bash
open /Applications/Postgres.app
```
Verify DB exists:
```bash
export PATH="/Applications/Postgres.app/Contents/Versions/16/bin:$PATH"
psql -U postgres -d ride_booking_db -c "\dt"
```
Expected: Tables `riders`, `rides`, `drivers`, `notifications`

### 2. Set Java Environment
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
java -version  # Should print: openjdk 17...
```

### 3. Start All 3 Services (3 separate terminals)

**Terminal 1 — Ride Service (port 8080)**
```bash
cd ~/Desktop/Capstone3/ride-booking-app/ride-service
mvn spring-boot:run -Plocal
```

**Terminal 2 — Driver Service (port 8081)**
```bash
cd ~/Desktop/Capstone3/ride-booking-app/driver-service
mvn spring-boot:run -Plocal
```

**Terminal 3 — Notification Service (port 8082)**
```bash
cd ~/Desktop/Capstone3/ride-booking-app/notification-service
mvn spring-boot:run -Plocal
```

### 4. Verify All Services Healthy
```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```
**Expected Response (all 3):**
```json
{ "status": "UP" }
```

### 5. Clean DB (Fresh Demo Start)
```bash
psql -U postgres -d ride_booking_db -c "TRUNCATE rides, riders, drivers, notifications RESTART IDENTITY CASCADE;"
```

---

## 🎬 SCENARIO 1 — Happy Path: Full Ride Lifecycle

> **Story:** Register a rider and driver → Book a ride → Event triggers auto-assignment → Complete the ride → Rate it  
> **Demonstrates:** All 3 services working together, event-driven coordination, Haversine matching within 5km

---

### STEP 1 · Register a Rider

**Postman:** `POST http://localhost:8080/api/riders`
```json
{
  "name": "Nischay HR",
  "phone": "9876543201",
  "email": "nischay@example.com"
}
```

**✅ Expected Response (201 Created):**
```json
{
  "riderId": "a1b2c3d4-...",
  "name": "Nischay HR",
  "phone": "9876543201",
  "email": "nischay@example.com",
  "createdAt": "2026-05-05T10:00:00"
}
```

**📋 Ride Service Log:**
```
[RideService] Rider registered | riderId=a1b2c3d4-...
```

> 💡 **Talk point:** *"We persist the rider to PostgreSQL and assign a UUID automatically via @PrePersist — no manual ID management needed."*

---

### STEP 2 · Register a Driver (Default Location 0,0)

**Postman:** `POST http://localhost:8081/api/drivers`
```json
{
  "name": "Ravi Kumar",
  "phone": "9876543210",
  "vehicleNumber": "KA01AB1234",
  "vehicleType": "CAR"
}
```

**✅ Expected Response (201 Created):**
```json
{
  "driverId": "d1e2f3g4-...",
  "name": "Ravi Kumar",
  "phone": "9876543210",
  "vehicleNumber": "KA01AB1234",
  "vehicleType": "CAR",
  "latitude": 0.0,
  "longitude": 0.0,
  "available": true,
  "rating": 5.0,
  "createdAt": "2026-05-05T10:01:00"
}
```

**📋 Driver Service Log:**
```
[DriverService] Driver registered | driverId=d1e2f3g4-...
```

> 💡 **Talk point:** *"Drivers start at (0,0) by default. We'll simulate real GPS coordinates using the PATCH location API. The matching algorithm uses Haversine distance with a 5km search radius."*

---

### STEP 3 · Move Driver Into Range (Within 5km of Pickup)

Our ride will start at `(12.97, 77.59)` — Tech Park, Bangalore.  
We place the driver at `(12.96, 77.60)` — approximately **1.3 km away**.

**Postman:** `PATCH http://localhost:8081/api/drivers/{{driverId}}/location`
```json
{
  "latitude": 12.96,
  "longitude": 77.60
}
```

**✅ Expected Response (200 OK):**
```json
{
  "driverId": "d1e2f3g4-...",
  "name": "Ravi Kumar",
  "latitude": 12.96,
  "longitude": 77.60,
  "available": true,
  "rating": 5.0
}
```

> 💡 **Talk point:** *"This simulates a real GPS update. In production, the driver mobile app would PATCH location every 30 seconds."*

---

### STEP 4 · Create a Ride (Triggers Event Chain!)

**Postman:** `POST http://localhost:8080/api/rides`
```json
{
  "riderId": "{{riderId}}",
  "pickupLocation": "Tech Park, Bangalore",
  "dropLocation": "Kempegowda International Airport",
  "pickupLat": 12.97,
  "pickupLng": 77.59
}
```

**✅ Expected Response (201 Created):**
```json
{
  "rideId": "r1s2t3u4-...",
  "riderId": "a1b2c3d4-...",
  "riderName": "Nischay HR",
  "driverId": null,
  "pickupLocation": "Tech Park, Bangalore",
  "dropLocation": "Kempegowda International Airport",
  "pickupLat": 12.97,
  "pickupLng": 77.59,
  "estimatedCost": 234.75,
  "status": "REQUESTED",
  "createdAt": "2026-05-05T10:02:00",
  "updatedAt": "2026-05-05T10:02:00"
}
```

**📋 Ride Service Logs:**
```
[RideService] Ride created | rideId=r1s2t3u4-... | riderId=a1b2c3d4-... | pickup=Tech Park, Bangalore | drop=Kempegowda International Airport
[RideService] RIDE_REQUESTED event published | rideId=r1s2t3u4-... | riderId=a1b2c3d4-...
```

**📋 Driver Service Logs (auto-triggered via event):**
```
[DriverService] Received RIDE_REQUESTED event | rideId=r1s2t3u4-... | pickup=Tech Park, Bangalore
[DriverService] Driver assigned | driverId=d1e2f3g4-... | rideId=r1s2t3u4-...
[DriverService] DRIVER_ASSIGNED event published | rideId=r1s2t3u4-... | driverId=d1e2f3g4-...
```

**📋 Notification Service Logs (auto-triggered via event):**
```
[NotificationService] Notification sent — DRIVER_ASSIGNED | rideId=r1s2t3u4-... | driverId=d1e2f3g4-...
```

> 💡 **Talk point:** *"Notice three things happening automatically: (1) Ride is saved as REQUESTED. (2) Atropos delivers the event to Driver Service which finds the nearest available driver within 5km using Haversine formula. (3) Atropos delivers DRIVER_ASSIGNED to Notification Service which creates a notification record. Zero polling, all event-driven."*

> 💡 **Talk point (Pricing):** *"The `estimatedCost` is generated at ride creation — simulating a 3rd-party pricing integration like Google Maps Distance Matrix API. In production, this would calculate fare based on distance × per-km rate."*

---

### STEP 5 · Confirm Driver Was Assigned

**Postman:** `GET http://localhost:8080/api/rides/{{rideId}}`

**✅ Expected Response — Status is now ASSIGNED:**
```json
{
  "rideId": "r1s2t3u4-...",
  "driverId": "d1e2f3g4-...",
  "status": "ASSIGNED",
  "estimatedCost": 234.75
}
```

> 💡 **Talk point:** *"The ride status transitioned from REQUESTED → ASSIGNED automatically. The driverId is now populated — this came through the DRIVER_ASSIGNED event consumed by Ride Service."*

---

### STEP 6 · Check Driver is Now Unavailable

**Postman:** `GET http://localhost:8081/api/drivers/{{driverId}}`

**✅ Expected Response:**
```json
{
  "driverId": "d1e2f3g4-...",
  "available": false,
  "rating": 5.0
}
```

**Postman:** `GET http://localhost:8081/api/drivers` (List Available)

**✅ Expected Response — Empty list:**
```json
[]
```

> 💡 **Talk point:** *"The driver is marked unavailable immediately upon assignment — preventing double-booking. The available drivers list is now empty."*

---

### STEP 7 · View the Notification Created

**Postman:** `GET http://localhost:8082/api/notifications`

**✅ Expected Response:**
```json
[
  {
    "notificationId": "n1n2n3n4-...",
    "userId": "d1e2f3g4-...",
    "message": "Driver d1e2f3g4-... assigned to ride r1s2t3u4-.... Vehicle: KA01AB1234. ETA: 10 mins",
    "type": "DRIVER_ASSIGNED",
    "sent": true,
    "read": false,
    "createdAt": "2026-05-05T10:02:05"
  }
]
```

---

### STEP 8 · Complete the Ride

**Postman:** `POST http://localhost:8080/api/rides/{{rideId}}/complete`

**✅ Expected Response:**
```json
{
  "rideId": "r1s2t3u4-...",
  "status": "COMPLETED",
  "driverId": "d1e2f3g4-..."
}
```

**📋 Ride Service Logs:**
```
[RideService] Ride completed | rideId=r1s2t3u4-...
[RideService] RIDE_COMPLETED event published | rideId=r1s2t3u4-... | driverId=d1e2f3g4-...
```

**📋 Notification Service Logs:**
```
[NotificationService] Notification sent — RIDE_COMPLETED | rideId=r1s2t3u4-... | riderId=a1b2c3d4-...
```

---

### STEP 9 · Rate the Ride

**Postman:** `PATCH http://localhost:8080/api/rides/{{rideId}}/rating`
```json
{
  "rating": 5,
  "feedback": "Smooth ride, very professional driver!"
}
```

**✅ Expected Response:**
```json
{
  "rideId": "r1s2t3u4-...",
  "status": "COMPLETED",
  "rating": 5,
  "feedback": "Smooth ride, very professional driver!"
}
```

---

### STEP 10 · Mark Notification as Read

**Postman:** `PATCH http://localhost:8082/api/notifications/{{notificationId}}/read`

**✅ Expected Response:**
```json
{
  "notificationId": "n1n2n3n4-...",
  "read": true,
  "type": "DRIVER_ASSIGNED"
}
```

**📋 Notification Service Log:**
```
[NotificationService] Notification marked as read | notificationId=n1n2n3n4-...
```

---

## 🎬 SCENARIO 2 — No Driver Available (5km Radius Check)

> **Story:** All drivers are beyond 5km → System gracefully handles no availability  
> **Demonstrates:** 5km radius enforcement, NO_DRIVER_AVAILABLE event, resilient event flow

---

### STEP 1 · Register a Second Driver (Beyond 5km)

Place driver at `(13.08, 77.59)` — approximately **12 km away** from pickup.

**Postman:** `POST http://localhost:8081/api/drivers`
```json
{
  "name": "Suresh Patil",
  "phone": "9876543299",
  "vehicleNumber": "KA05XY9999",
  "vehicleType": "AUTO"
}
```

Then set location beyond 5km:

**Postman:** `PATCH http://localhost:8081/api/drivers/{{driverId2}}/location`
```json
{
  "latitude": 13.08,
  "longitude": 77.59
}
```

> 💡 *Verify: Haversine distance from (12.97, 77.59) to (13.08, 77.59) ≈ 12.2 km → outside 5km radius.*

---

### STEP 2 · Create a New Ride

**Postman:** `POST http://localhost:8080/api/rides`
```json
{
  "riderId": "{{riderId}}",
  "pickupLocation": "Tech Park, Bangalore",
  "dropLocation": "Whitefield",
  "pickupLat": 12.97,
  "pickupLng": 77.59
}
```

**✅ Expected Response (201 Created):**
```json
{
  "status": "REQUESTED",
  "estimatedCost": 189.50
}
```

**📋 Driver Service Logs:**
```
[DriverService] Received RIDE_REQUESTED event | rideId=... | pickup=Tech Park, Bangalore
[DriverService] No driver available for ride: r5r6r7r8-...
[DriverService] NO_DRIVER_AVAILABLE event published | rideId=r5r6r7r8-... | reason=No available drivers in the system
```

**📋 Ride Service Logs (event consumed + status updated):**
```
[RideService] Ride created | rideId=r5r6r7r8-...
[RideService] RIDE_REQUESTED event published | rideId=r5r6r7r8-...
[RideService] Consumed NO_DRIVER_AVAILABLE event | rideId=r5r6r7r8-... | reason=No available drivers in the system
[RideService] Ride status updated to NO_DRIVER_AVAILABLE | rideId=r5r6r7r8-...
```

**Postman:** `GET http://localhost:8080/api/rides/{{rideId2}}`

**✅ Expected Response — Status is now NO_DRIVER_AVAILABLE:**
```json
{
  "rideId": "r5r6r7r8-...",
  "driverId": null,
  "status": "NO_DRIVER_AVAILABLE",
  "estimatedCost": 189.50
}
```

> 💡 **Talk point:** *"Notice the event chain: Driver Service publishes NO_DRIVER_AVAILABLE → Ride Service consumes it via webhook → ride status automatically transitions to NO_DRIVER_AVAILABLE. The algorithm filtered out Suresh's driver because he's 12km away — beyond the 5km search radius. The status change is now permanent in the DB — no dangling states."*

---

### STEP 3 · Move Driver Into Range and Demonstrate Recovery

**Postman:** `PATCH http://localhost:8081/api/drivers/{{driverId2}}/location`
```json
{
  "latitude": 12.96,
  "longitude": 77.60
}
```

Now create another ride — Suresh will be matched this time.

> 💡 **Talk point:** *"This is how you'd simulate a dynamic city where drivers are constantly moving. In production, drivers ping location every 30 seconds. This PATCH API simulates that."*

---

## 🎬 SCENARIO 3 — Ride Cancellation Flow

> **Story:** Rider cancels a ride mid-way → Event triggers notification  
> **Demonstrates:** State machine validation, RIDE_CANCELLED event, negative path handling

---

### STEP 1 · Create a Ride

(Follow Scenario 1 Step 4 — same request)

### STEP 2 · Cancel the Ride

**Postman:** `POST http://localhost:8080/api/rides/{{rideId}}/cancel`

**✅ Expected Response:**
```json
{
  "rideId": "...",
  "status": "CANCELLED"
}
```

**📋 Ride Service Logs:**
```
[RideService] Ride cancelled | rideId=...
[RideService] RIDE_CANCELLED event published | rideId=...
```

**📋 Notification Service Logs:**
```
[NotificationService] Notification sent — RIDE_CANCELLED | rideId=... | riderId=...
```

### STEP 3 · Prove State Machine Guards (Negative Test)

Try to cancel again:

**Postman:** `POST http://localhost:8080/api/rides/{{rideId}}/cancel`

**✅ Expected Response (400 Bad Request):**
```json
{
  "error": "Cannot cancel a ride in status: CANCELLED"
}
```

Try to complete a cancelled ride:

**Postman:** `POST http://localhost:8080/api/rides/{{rideId}}/complete`

**✅ Expected Response (400 Bad Request):**
```json
{
  "error": "Cannot complete a ride in status: CANCELLED"
}
```

> 💡 **Talk point:** *"The ride lifecycle is enforced via state machine validation — invalid transitions throw meaningful errors. This prevents data corruption in the DB."*

---

## 🎬 SCENARIO 4 — Multi-Driver Tie-Breaking

> **Story:** Two drivers equidistant from pickup — system picks the higher-rated one  
> **Demonstrates:** 3-level tie-breaking algorithm (distance → rating → registration time)

---

### STEP 1 · Register Two Drivers at Same Distance

**Driver A** (5.0 rating — default):
```json
{
  "name": "Amit Singh",
  "phone": "9876500001",
  "vehicleNumber": "KA01AA0001",
  "vehicleType": "CAR"
}
```
Set location: `{ "latitude": 12.972, "longitude": 77.592 }` ← ~0.5km from pickup

**Driver B** (update rating manually or note it starts at 5.0 — registered earlier):
```json
{
  "name": "Vijay Nair",
  "phone": "9876500002",
  "vehicleNumber": "KA01AA0002",
  "vehicleType": "CAR"
}
```
Set same location: `{ "latitude": 12.972, "longitude": 77.592 }`

### STEP 2 · Create a Ride

Watch logs — the driver registered **earlier** wins the tie (same distance, same rating → earliest `createdAt`).

**📋 Driver Service Logs:**
```
[DriverService] Driver assigned | driverId=<Amit's ID> | rideId=...
```

> 💡 **Talk point:** *"The matching algorithm has three priority levels: nearest distance first, then highest rating for ties, then earliest registration as a final tiebreaker. This ensures deterministic, fair assignment — no random outcomes."*

---

## 🎬 SCENARIO 5 — Full Data Inspection (Proof APIs)

> **Purpose:** Show all data stored across services — useful for demo evidence

### All Riders
**Postman:** `GET http://localhost:8080/api/riders`

### All Rides
**Postman:** `GET http://localhost:8080/api/rides/all`

### All Drivers (Including Unavailable)
**Postman:** `GET http://localhost:8081/api/drivers/all`

### Available Drivers Only
**Postman:** `GET http://localhost:8081/api/drivers`

### Driver Stats
**Postman:** `GET http://localhost:8081/api/drivers/{{driverId}}/stats`

**✅ Expected Response:**
```json
{
  "driverId": "...",
  "name": "Ravi Kumar",
  "vehicleType": "CAR",
  "vehicleNumber": "KA01AB1234",
  "rating": 5.0,
  "available": false,
  "totalAvailableDrivers": 0
}
```

### All Notifications
**Postman:** `GET http://localhost:8082/api/notifications`

### Notifications by User
**Postman:** `GET http://localhost:8082/api/notifications/{{driverId}}`

---

## 📊 Complete Event Flow Summary

```
RIDE CREATION FLOW:
───────────────────
Rider POSTs /api/rides
  → Ride saved (status: REQUESTED)
  → estimatedCost generated (₹50–₹500, simulates 3rd-party pricing)
  → RIDE_REQUESTED event published to Atropos
    → Driver Service receives event
      → Queries all available drivers
      → Filters to within 5km (Haversine distance)
      → Picks nearest driver (tie-break: rating → createdAt)
      → If FOUND:
          Driver marked available=false
          DRIVER_ASSIGNED event published
          → Notification Service receives event
            → Notification saved (type: DRIVER_ASSIGNED)
      → If NOT FOUND:
          NO_DRIVER_AVAILABLE event published
          → Ride Service receives event via webhook
            → Ride status updated: REQUESTED → NO_DRIVER_AVAILABLE
            → Logged in Ride Service output

CANCELLATION FLOW:
──────────────────
Rider POSTs /api/rides/{id}/cancel
  → Ride status → CANCELLED
  → RIDE_CANCELLED event published to Atropos
    → Notification Service receives event
      → Notification saved (type: RIDE_CANCELLED)

COMPLETION FLOW:
────────────────
POST /api/rides/{id}/complete
  → Ride status → COMPLETED
  → RIDE_COMPLETED event published to Atropos
    → Notification Service receives event
      → Notification saved (type: RIDE_COMPLETED)
```

---

## 📋 Quick API Reference Cheatsheet

| # | Service | Method | Endpoint | Description |
|---|---------|--------|----------|-------------|
| 1 | Ride (8080) | POST | `/api/riders` | Register rider |
| 2 | Ride (8080) | GET | `/api/riders` | List all riders |
| 3 | Ride (8080) | GET | `/api/riders/{id}` | Get rider by ID |
| 4 | Ride (8080) | POST | `/api/rides` | **Create ride** → triggers event chain |
| 5 | Ride (8080) | GET | `/api/rides/all` | All rides |
| 6 | Ride (8080) | GET | `/api/rides/{id}` | Get ride by ID |
| 7 | Ride (8080) | GET | `/api/rides?riderId=` | Rides by rider |
| 8 | Ride (8080) | POST | `/api/rides/{id}/cancel` | Cancel ride |
| 9 | Ride (8080) | POST | `/api/rides/{id}/complete` | Complete ride |
| 10 | Ride (8080) | PATCH | `/api/rides/{id}/rating` | Rate completed ride |
| 10b | Ride (8080) | POST | `/api/rides/events/no-driver-available/webhook` | **Webhook — Atropos delivers NO_DRIVER_AVAILABLE event** |
| 11 | Driver (8081) | POST | `/api/drivers` | Register driver |
| 12 | Driver (8081) | GET | `/api/drivers` | List available drivers |
| 13 | Driver (8081) | GET | `/api/drivers/all` | List all drivers |
| 14 | Driver (8081) | GET | `/api/drivers/{id}` | Driver details |
| 15 | Driver (8081) | GET | `/api/drivers/{id}/stats` | Driver stats |
| 16 | Driver (8081) | PATCH | `/api/drivers/{id}/availability` | Toggle availability |
| 17 | Driver (8081) | PATCH | `/api/drivers/{id}/location` | **Update GPS location** |
| 18 | Notif (8082) | GET | `/api/notifications` | All notifications |
| 19 | Notif (8082) | GET | `/api/notifications/{userId}` | Notifications by user |
| 20 | Notif (8082) | PATCH | `/api/notifications/{id}/read` | Mark as read |
| 21 | Ride (8080) | GET | `/actuator/health` | Health check |
| 22 | Driver (8081) | GET | `/actuator/health` | Health check |
| 23 | Notif (8082) | GET | `/actuator/health` | Health check |

---

## 💡 Key Technical Talking Points

### 1. Event-Driven Architecture
> *"Services communicate only via Atropos events — complete decoupling. Ride Service doesn't know Driver Service exists. If Driver Service is down, rides still get created. Events are durable in Kinesis."*

### 2. Haversine Matching Algorithm
> *"We use the Haversine formula to calculate great-circle distances on Earth's surface — accurate to within meters. The 5km search radius is configurable as a constant and can be externalised to application.properties for environment-specific tuning."*

### 3. Tie-Breaking Strategy
> *"When two drivers are equidistant — which happens when multiple drivers are at default (0,0) — we apply a deterministic 3-tier comparator: distance → rating (higher is better) → registration time (earlier wins). No random assignment, no ambiguity."*

### 4. Simulated Pricing (3rd-Party Integration Point)
> *"The `estimatedCost` is generated using ThreadLocalRandom as a placeholder for a real pricing engine — Google Maps Distance Matrix, OSRM, or an internal fare calculator. The integration point is cleanly isolated in `RideService.createRide()`, making it a one-line swap in production."*

### 5. State Machine Validation
> *"The ride lifecycle (REQUESTED → ASSIGNED → COMPLETED / CANCELLED) is enforced via explicit state checks. Invalid transitions return descriptive 400 errors — no invalid states can be persisted to the DB."*

### 6. Default Driver Location Design Decision
> *"Drivers default to (0,0) — the Null Island in the Atlantic Ocean. This ensures new drivers are immediately visible in the system but won't be matched until they actively set their GPS location, which simulates a real driver going online."*

### 7. Event-Driven State Mutations (No Driver Available → NO_DRIVER_AVAILABLE Status)
> *"When Driver Service can't find a driver within 5km, it publishes NO_DRIVER_AVAILABLE event to Atropos. Ride Service consumes this event via webhook and atomically updates the ride status to NO_DRIVER_AVAILABLE. This is **not a query or poll** — the status change is reactive and logged. The 5-step event chain (create ride → RIDE_REQUESTED → driver search → NO_DRIVER_AVAILABLE → status update) happens end-to-end without explicit orchestration."*

---

## 🏁 Recommended Demo Order for Interview

```
1. Health Checks          → Show all 3 services are UP
2. Register Rider         → riderId in clipboard
3. Register Driver        → driverId in clipboard (notice available=true, location=0,0)
4. Create Ride #1         → SHOW: status=REQUESTED, estimatedCost (random price!)
   └─ POINT TO LOGS: event chain across all 3 services
5. Get Ride #1            → SHOW: status=ASSIGNED, driverId populated
6. Get Driver             → SHOW: available=false (auto-set by event)
7. Get Notifications      → SHOW: DRIVER_ASSIGNED notification created automatically
8. Complete Ride          → SHOW: RIDE_COMPLETED event + new notification
9. Rate Ride              → SHOW: feedback stored
10. Mark Notification Read→ SHOW: read=true
--- SCENARIO 2: No Driver ---
11. Register 2nd Driver   → Place at 13.08, 77.59 (beyond 5km)
12. Create Ride #2        → SHOW logs: NO_DRIVER_AVAILABLE event published by driver-service
13. Get Ride #2           → SHOW: status=NO_DRIVER_AVAILABLE, driverId=null (fully event-driven state change!)
    └─ POINT TO LOGS: Ride Service consumed event + updated status
--- SCENARIO 3: Cancellation ---
14. Create Ride #3        → Then cancel it
15. Try cancel again      → SHOW: 400 error (state machine guard)
--- DATA DUMP ---
16. GET /api/rides/all    → Show all rides with statuses (ASSIGNED, NO_DRIVER_AVAILABLE, COMPLETED, CANCELLED)
17. GET /api/drivers/all  → Show all drivers with locations
18. GET /api/notifications→ Show all notification types
```

---

*Built with: Java 17 · Spring Boot 3.5.7 · PostgreSQL 16 · Atropos Event Bus · Haversine Geospatial Matching · Simulated Fare Pricing*
