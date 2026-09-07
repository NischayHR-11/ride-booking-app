-- Create riders table
CREATE TABLE IF NOT EXISTS riders (
    rider_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT NOT NULL UNIQUE,
    email TEXT NOT NULL UNIQUE,
    role TEXT NOT NULL DEFAULT 'RIDER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create rides table
CREATE TABLE IF NOT EXISTS rides (
    ride_id TEXT PRIMARY KEY,
    rider_id TEXT NOT NULL REFERENCES riders(rider_id),
    driver_id TEXT,
    pickup_location TEXT NOT NULL,
    drop_location TEXT NOT NULL,
    pickup_lat DOUBLE PRECISION,
    pickup_lng DOUBLE PRECISION,
    estimated_cost DOUBLE PRECISION,
    status TEXT NOT NULL,
    rating INTEGER,
    feedback TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create updated_at trigger function
CREATE OR REPLACE FUNCTION trigger_set_updated_at_timestamp() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
    BEGIN
      NEW.updated_at = CURRENT_TIMESTAMP;
      RETURN NEW;
    END;
$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'rides_updated_at_timestamp') THEN
        CREATE TRIGGER rides_updated_at_timestamp BEFORE UPDATE ON rides FOR EACH ROW
        EXECUTE PROCEDURE trigger_set_updated_at_timestamp();
    END IF;
END;
$$;
