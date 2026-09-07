package com.ridebooking.driverservice.service;

import com.ridebooking.driverservice.entity.Driver;

import java.util.List;
import java.util.Optional;

/*
 * Design Pattern: STRATEGY
 *
 * This interface defines the interchangeable algorithm contract for selecting the
 * best available driver for a given ride request.
 *
 * WHY: Driver matching logic can evolve independently of the service layer.
 *   Different strategies (nearest, highest-rated, zone-based, surge-aware, etc.)
 *   can be swapped or tested without touching DriverService.
 *
 * WHERE:
 *   — Interface (strategy contract):    DriverMatchingStrategy          ← here
 *   — Concrete strategy (default):      NearestDriverMatchingStrategy
 *   — Context (uses the strategy):      DriverService.handleRideRequested()
 *
 * Spring's DI wires the active strategy automatically; add @Primary or a
 * configuration property to switch implementations at runtime.
 */
public interface DriverMatchingStrategy {
    Optional<Driver> findBestDriver(List<Driver> availableDrivers, double pickupLat, double pickupLng);
}
