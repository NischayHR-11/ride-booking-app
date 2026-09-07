package com.ridebooking.driverservice.service;

import com.ridebooking.driverservice.entity.Driver;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/*
 * Design Pattern: STRATEGY — Concrete Implementation
 *
 * NearestDriverMatchingStrategy is the default concrete strategy injected
 * into DriverService. It implements DriverMatchingStrategy (the strategy
 * contract) and can be replaced with any other implementation without
 * modifying the service.
 *
 * Matching criteria (in priority order):
 *   1. Primary  — shortest Haversine distance to pickup (must be ≤ 5 km)
 *   2. Secondary — highest rating (tie-break on distance)
 *   3. Tertiary  — earliest registration date (tie-break on rating)
 */
@Component
public class NearestDriverMatchingStrategy implements DriverMatchingStrategy {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double MAX_SEARCH_RADIUS_KM = 5.0; // Drivers beyond 5 km are not considered

    @Override
    public Optional<Driver> findBestDriver(List<Driver> availableDrivers, double pickupLat, double pickupLng) {
        return availableDrivers.stream()
                // Filter: only drivers within MAX_SEARCH_RADIUS_KM
                .filter(d -> haversineDistance(d.getLatitude(), d.getLongitude(), pickupLat, pickupLng) <= MAX_SEARCH_RADIUS_KM)
                .min((d1, d2) -> {
                    // Primary: distance (ascending)
                    double dist1 = haversineDistance(d1.getLatitude(), d1.getLongitude(), pickupLat, pickupLng);
                    double dist2 = haversineDistance(d2.getLatitude(), d2.getLongitude(), pickupLat, pickupLng);
                    int distCompare = Double.compare(dist1, dist2);
                    if (distCompare != 0) return distCompare;

                    // Secondary: rating (descending)
                    int ratingCompare = Double.compare(d2.getRating(), d1.getRating());
                    if (ratingCompare != 0) return ratingCompare;

                    // Tertiary: createdAt (ascending — earliest registered wins)
                    return d1.getCreatedAt().compareTo(d2.getCreatedAt());
                });
    }

    /** Haversine formula — great-circle distance between two GPS coordinates (km). */
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
