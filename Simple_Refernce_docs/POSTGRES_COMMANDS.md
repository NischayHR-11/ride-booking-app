# PostgreSQL Commands - Data Inspection Guide

**Database:** `ride_booking_db`  
**Host:** localhost  
**Port:** 5432  
**User:** postgres  
**Password:** postgres

---

## 🔗 CONNECT TO POSTGRESQL

### Option 1: Using psql Command Line
```bash
# Connect to the database
psql -h localhost -U postgres -d ride_booking_db

# If prompted for password, enter: postgres

# Or in one command with password
PGPASSWORD=postgres psql -h localhost -U postgres -d ride_booking_db
```

### Option 2: Connection String
```bash
postgresql://postgres:postgres@localhost:5432/ride_booking_db
```

---

## 📋 BASIC COMMANDS (Once Connected)

### List All Databases
```sql
\l
```
**Output:**
```
                                     List of databases
        Name        | Owner    | Encoding |   Collate   |    Ctype    | Access privileges
-------------------+----------+----------+-------------+-------------+-------------------
 postgres          | postgres | UTF8     | en_US.UTF-8 | en_US.UTF-8 |
 ride_booking_db   | postgres | UTF8     | en_US.UTF-8 | en_US.UTF-8 |
 template0         | postgres | UTF8     | en_US.UTF-8 | en_US.UTF-8 | ...
 template1         | postgres | UTF8     | en_US.UTF-8 | en_US.UTF-8 | ...
```

### Switch to Database
```sql
\c ride_booking_db
```
**Output:**
```
You are now connected to database "ride_booking_db" as user "postgres".
```

### List All Tables
```sql
\dt
```
**Output:**
```
                 List of relations
 Schema |      Name       | Type  | Owner
--------+-----------------+-------+----------
 public | drivers         | table | postgres
 public | notifications   | table | postgres
 public | rides           | table | postgres
 public | ride_events     | table | postgres
(4 rows)
```

### Describe Table Structure
```sql
\d rides
```
**Output:**
```
                                     Table "public.rides"
     Column      |            Type             | Collation | Nullable |      Default
-----------------+-----------------------------+-----------+----------+-------------------
 ride_id         | uuid                        |           | not null | 
 rider_id        | character varying(255)      |           | not null |
 driver_id       | character varying(255)      |           |          |
 pickup_location | character varying(255)      |           | not null |
 drop_location   | character varying(255)      |           | not null |
 status          | character varying(50)       |           |          |
 rating          | integer                     |           |          |
 feedback        | character varying(1000)     |           |          |
 created_at      | timestamp without time zone |           |          |
 updated_at      | timestamp without time zone |           |          |
Indexes:
    "rides_pkey" PRIMARY KEY, btree (ride_id)
Constraints:
    CHECK (status IN ('REQUESTED', 'CANCELLED', 'COMPLETED'))
```

---

## 🚗 RIDES TABLE

### View All Rides
```sql
SELECT * FROM rides;
```

### View All Columns with Better Formatting
```sql
SELECT * FROM rides \g
```

### View Specific Columns
```sql
SELECT ride_id, rider_id, driver_id, status, created_at FROM rides;
```

### Count Total Rides
```sql
SELECT COUNT(*) as total_rides FROM rides;
```

### View Rides by Rider
```sql
SELECT * FROM rides WHERE rider_id = 'rider-001';
```

### View Rides by Status
```sql
-- All requested rides
SELECT * FROM rides WHERE status = 'REQUESTED';

-- All completed rides
SELECT * FROM rides WHERE status = 'COMPLETED';

-- All cancelled rides
SELECT * FROM rides WHERE status = 'CANCELLED';
```

### View Completed Rides with Ratings
```sql
SELECT ride_id, rider_id, driver_id, rating, feedback, created_at 
FROM rides 
WHERE status = 'COMPLETED' AND rating IS NOT NULL;
```

### View Rides for Specific Driver
```sql
SELECT * FROM rides WHERE driver_id = 'driver-001';
```

### View Rides by Date Range
```sql
SELECT * FROM rides 
WHERE created_at >= '2026-05-03' AND created_at < '2026-05-04';
```

### View Recent Rides
```sql
SELECT * FROM rides 
ORDER BY created_at DESC 
LIMIT 10;
```

### Get Ride Statistics
```sql
SELECT 
  status, 
  COUNT(*) as count,
  AVG(rating) as avg_rating
FROM rides 
WHERE status = 'COMPLETED'
GROUP BY status;
```

---

## 👨‍💼 DRIVERS TABLE

### View All Drivers
```sql
SELECT * FROM drivers;
```

### View Available Drivers
```sql
SELECT * FROM drivers WHERE available = true;
```

### Count Available Drivers
```sql
SELECT COUNT(*) as available_count FROM drivers WHERE available = true;
```

### View Driver Details with Rating
```sql
SELECT driver_id, name, phone, vehicle_number, vehicle_type, rating, available 
FROM drivers;
```

### View Drivers with Location
```sql
SELECT driver_id, name, latitude, longitude, available 
FROM drivers;
```

### Get Drivers Statistics
```sql
SELECT 
  vehicle_type,
  COUNT(*) as total_drivers,
  SUM(CASE WHEN available = true THEN 1 ELSE 0 END) as available_count,
  AVG(rating) as avg_rating
FROM drivers
GROUP BY vehicle_type;
```

### View Drivers by Vehicle Type
```sql
SELECT * FROM drivers WHERE vehicle_type = 'CAR';
```

### High-Rated Drivers
```sql
SELECT driver_id, name, rating 
FROM drivers 
WHERE rating >= 4.5 
ORDER BY rating DESC;
```

---

## 🔔 NOTIFICATIONS TABLE

### View All Notifications
```sql
SELECT * FROM notifications;
```

### Count Total Notifications
```sql
SELECT COUNT(*) as total_notifications FROM notifications;
```

### View Unread Notifications
```sql
SELECT * FROM notifications WHERE read = false;
```

### Count Unread Notifications
```sql
SELECT COUNT(*) as unread_count FROM notifications WHERE read = false;
```

### View Notifications for Specific User
```sql
SELECT * FROM notifications WHERE user_id = 'rider-001';
```

### Unread Notifications for User
```sql
SELECT * FROM notifications 
WHERE user_id = 'rider-001' AND read = false;
```

### View by Notification Type
```sql
SELECT * FROM notifications WHERE type = 'DRIVER_ASSIGNED';
```

### Get Notification Statistics
```sql
SELECT 
  type,
  COUNT(*) as total,
  SUM(CASE WHEN read = true THEN 1 ELSE 0 END) as read_count,
  SUM(CASE WHEN read = false THEN 1 ELSE 0 END) as unread_count
FROM notifications
GROUP BY type;
```

### View Recent Notifications
```sql
SELECT * FROM notifications 
ORDER BY created_at DESC 
LIMIT 20;
```

---

## 📊 ADVANCED QUERIES

### Get Revenue Stats (Completed Rides with Ratings)
```sql
SELECT 
  COUNT(*) as completed_rides,
  AVG(rating) as avg_rating,
  MIN(rating) as min_rating,
  MAX(rating) as max_rating
FROM rides 
WHERE status = 'COMPLETED';
```

### Get Active Users (Riders with Rides)
```sql
SELECT DISTINCT rider_id, COUNT(*) as ride_count 
FROM rides 
GROUP BY rider_id 
ORDER BY ride_count DESC;
```

### Get Active Drivers (Drivers with Assigned Rides)
```sql
SELECT DISTINCT driver_id, COUNT(*) as assigned_rides 
FROM rides 
WHERE driver_id IS NOT NULL
GROUP BY driver_id 
ORDER BY assigned_rides DESC;
```

### Match Rides with Drivers
```sql
SELECT 
  r.ride_id,
  r.rider_id,
  r.status,
  d.driver_id,
  d.name as driver_name,
  d.vehicle_type,
  r.created_at
FROM rides r
LEFT JOIN drivers d ON r.driver_id = d.driver_id
ORDER BY r.created_at DESC;
```

### Rides by Hour
```sql
SELECT 
  DATE_TRUNC('hour', created_at) as hour,
  COUNT(*) as ride_count,
  COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as completed
FROM rides
GROUP BY DATE_TRUNC('hour', created_at)
ORDER BY hour DESC;
```

### Top Rated Drivers
```sql
SELECT 
  d.driver_id,
  d.name,
  d.vehicle_type,
  d.rating,
  COUNT(r.ride_id) as total_rides,
  COUNT(CASE WHEN r.status = 'COMPLETED' THEN 1 END) as completed_rides
FROM drivers d
LEFT JOIN rides r ON d.driver_id = r.driver_id
GROUP BY d.driver_id, d.name, d.vehicle_type, d.rating
ORDER BY d.rating DESC;
```

---

## 🗑️ DATA CLEANUP COMMANDS

### Delete All Rides (for testing)
```sql
DELETE FROM rides;
```

### Delete All Drivers (for testing)
```sql
DELETE FROM drivers;
```

### Delete All Notifications (for testing)
```sql
DELETE FROM notifications;
```

### Delete Specific Ride
```sql
DELETE FROM rides WHERE ride_id = '550e8400-e29b-41d4-a716-446655440000';
```

### Clear Everything and Reset
```sql
DELETE FROM notifications;
DELETE FROM rides;
DELETE FROM drivers;
```

---

## 📤 EXPORT DATA

### Export to CSV
```bash
# Exit psql first if connected (\q)

# Export rides to CSV
PGPASSWORD=postgres psql -h localhost -U postgres -d ride_booking_db \
  -c "COPY rides TO STDOUT WITH CSV HEADER" > rides.csv

# Export drivers to CSV
PGPASSWORD=postgres psql -h localhost -U postgres -d ride_booking_db \
  -c "COPY drivers TO STDOUT WITH CSV HEADER" > drivers.csv

# Export notifications to CSV
PGPASSWORD=postgres psql -h localhost -U postgres -d ride_booking_db \
  -c "COPY notifications TO STDOUT WITH CSV HEADER" > notifications.csv
```

---

## ✅ USEFUL PSQL SHORTCUTS

| Command | Purpose |
|---------|---------|
| `\d` | List all tables |
| `\d tablename` | Describe table structure |
| `\l` | List all databases |
| `\c dbname` | Connect to database |
| `\q` | Quit psql |
| `\h` | Help on SQL commands |
| `\?` | Help on psql commands |
| `\e` | Open editor |
| `\i filename` | Execute SQL from file |
| `\o filename` | Send output to file |
| `\copy (query) TO 'file'` | Export query results |

---

## 🔄 PRACTICAL WORKFLOW

### 1. Connect to Database
```bash
PGPASSWORD=postgres psql -h localhost -U postgres -d ride_booking_db
```

### 2. Check Tables Exist
```sql
\dt
```

### 3. View Table Structures
```sql
\d rides
\d drivers
\d notifications
```

### 4. Check Current Data
```sql
SELECT COUNT(*) as ride_count FROM rides;
SELECT COUNT(*) as driver_count FROM drivers;
SELECT COUNT(*) as notification_count FROM notifications;
```

### 5. View Recent Data
```sql
SELECT * FROM rides ORDER BY created_at DESC LIMIT 5;
SELECT * FROM drivers ORDER BY created_at DESC LIMIT 5;
SELECT * FROM notifications ORDER BY created_at DESC LIMIT 5;
```

### 6. Analyze Data
```sql
SELECT status, COUNT(*) FROM rides GROUP BY status;
SELECT AVG(rating) FROM rides WHERE rating IS NOT NULL;
SELECT COUNT(DISTINCT rider_id) FROM rides;
```

### 7. Exit
```sql
\q
```

---

## 💡 QUICK ONE-LINERS

### Check if Data Exists
```bash
PGPASSWORD=postgres psql -h localhost -U postgres -d ride_booking_db -c "SELECT COUNT(*) FROM rides; SELECT COUNT(*) FROM drivers; SELECT COUNT(*) FROM notifications;"
```

### View All Data in Tables
```bash
PGPASSWORD=postgres psql -h localhost -U postgres -d ride_booking_db -c "SELECT * FROM rides LIMIT 5; SELECT * FROM drivers LIMIT 5; SELECT * FROM notifications LIMIT 5;"
```

### Get Database Size
```bash
PGPASSWORD=postgres psql -h localhost -U postgres -d ride_booking_db -c "SELECT pg_size_pretty(pg_database_size('ride_booking_db'));"
```

### List Table Sizes
```bash
PGPASSWORD=postgres psql -h localhost -U postgres -d ride_booking_db -c "SELECT tablename, pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size FROM pg_tables WHERE schemaname != 'pg_catalog' ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;"
```

