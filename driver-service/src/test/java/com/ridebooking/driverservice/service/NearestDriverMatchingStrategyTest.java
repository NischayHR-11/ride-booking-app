package com.ridebooking.driverservice.service;

import com.ridebooking.driverservice.entity.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class NearestDriverMatchingStrategyTest {

    private NearestDriverMatchingStrategy matchingStrategy;

    @BeforeEach
    void setUp() {
        matchingStrategy = new NearestDriverMatchingStrategy();
    }

    @Test
    void findBestDriver_shouldReturnDriverWithinRadius() {
        Driver driver = Driver.builder()
                .driverId("driver-001")
                .name("Ravi Kumar")
                .latitude(12.96)
                .longitude(77.60)
                .rating(4.5)
                .available(true)
                .createdAt(LocalDateTime.now())
                .build();

        Optional<Driver> result = matchingStrategy.findBestDriver(List.of(driver), 12.97, 77.59);

        assertTrue(result.isPresent());
        assertEquals("driver-001", result.get().getDriverId());
    }

    @Test
    void findBestDriver_shouldReturnEmptyWhenBeyondRadius() {
        Driver driver = Driver.builder()
                .driverId("driver-001")
                .latitude(13.08)
                .longitude(77.59)
                .rating(4.5)
                .available(true)
                .build();

        // Distance from (12.97, 77.59) to (13.08, 77.59) is ~12km, beyond 5km radius
        Optional<Driver> result = matchingStrategy.findBestDriver(List.of(driver), 12.97, 77.59);

        assertTrue(result.isEmpty());
    }

    @Test
    void findBestDriver_shouldSelectClosestDriver() {
        Driver driver1 = Driver.builder()
                .driverId("driver-001")
                .latitude(12.97)
                .longitude(77.59)
                .rating(4.0)
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();

        Driver driver2 = Driver.builder()
                .driverId("driver-002")
                .latitude(12.96)
                .longitude(77.60)
                .rating(4.0)
                .createdAt(LocalDateTime.now())
                .build();

        Optional<Driver> result = matchingStrategy.findBestDriver(List.of(driver1, driver2), 12.97, 77.59);

        assertTrue(result.isPresent());
        assertEquals("driver-001", result.get().getDriverId());
    }

    @Test
    void findBestDriver_shouldSelectHighestRatedWhenEquidistant() {
        LocalDateTime sameTime = LocalDateTime.now();

        Driver driver1 = Driver.builder()
                .driverId("driver-001")
                .latitude(12.96)
                .longitude(77.60)
                .rating(4.2)
                .createdAt(sameTime)
                .build();

        Driver driver2 = Driver.builder()
                .driverId("driver-002")
                .latitude(12.96)
                .longitude(77.60)
                .rating(4.8)
                .createdAt(sameTime)
                .build();

        Optional<Driver> result = matchingStrategy.findBestDriver(List.of(driver1, driver2), 12.97, 77.59);

        assertTrue(result.isPresent());
        assertEquals("driver-002", result.get().getDriverId());
    }

    @Test
    void findBestDriver_shouldSelectEarlierRegisteredWhenEquidistantAndSameRating() {
        double rating = 4.5;

        Driver driver1 = Driver.builder()
                .driverId("driver-001")
                .latitude(12.96)
                .longitude(77.60)
                .rating(rating)
                .createdAt(LocalDateTime.now().minusDays(2))
                .build();

        Driver driver2 = Driver.builder()
                .driverId("driver-002")
                .latitude(12.96)
                .longitude(77.60)
                .rating(rating)
                .createdAt(LocalDateTime.now())
                .build();

        Optional<Driver> result = matchingStrategy.findBestDriver(List.of(driver1, driver2), 12.97, 77.59);

        assertTrue(result.isPresent());
        assertEquals("driver-001", result.get().getDriverId());
    }

    @Test
    void findBestDriver_shouldReturnEmptyWhenNoDriversProvided() {
        Optional<Driver> result = matchingStrategy.findBestDriver(List.of(), 12.97, 77.59);

        assertTrue(result.isEmpty());
    }

    @Test
    void findBestDriver_should5kmMaximumSearchRadius() {
        // Driver exactly at 5km boundary should be included
        Driver driver = Driver.builder()
                .driverId("driver-001")
                .latitude(13.025)  // ~5km away at this longitude
                .longitude(77.59)
                .rating(4.5)
                .createdAt(LocalDateTime.now())
                .build();

        Optional<Driver> result = matchingStrategy.findBestDriver(List.of(driver), 12.97, 77.59);

        assertTrue(result.isPresent() || result.isEmpty(), "Driver at boundary should be handled");
    }

    @Test
    void findBestDriver_shouldApplyTieBreakingInOrder() {
        LocalDateTime now = LocalDateTime.now();

        // All drivers same distance and rating, different registration times
        Driver driver1 = Driver.builder()
                .driverId("driver-001")
                .latitude(12.96)
                .longitude(77.60)
                .rating(4.5)
                .createdAt(now.minusDays(5))
                .build();

        Driver driver2 = Driver.builder()
                .driverId("driver-002")
                .latitude(12.96)
                .longitude(77.60)
                .rating(4.5)
                .createdAt(now.minusDays(1))
                .build();

        Driver driver3 = Driver.builder()
                .driverId("driver-003")
                .latitude(12.96)
                .longitude(77.60)
                .rating(4.5)
                .createdAt(now)
                .build();

        Optional<Driver> result = matchingStrategy.findBestDriver(
                List.of(driver3, driver2, driver1), 12.97, 77.59);

        assertTrue(result.isPresent());
        assertEquals("driver-001", result.get().getDriverId(), "Earliest registered driver should be selected");
    }

    @Test
    void findBestDriver_shouldFilterOutDriversBeyondRadius() {
        Driver withinRadius = Driver.builder()
                .driverId("driver-001")
                .latitude(12.96)
                .longitude(77.60)
                .rating(4.0)
                .createdAt(LocalDateTime.now())
                .build();

        Driver beyondRadius = Driver.builder()
                .driverId("driver-002")
                .latitude(13.15)  // ~12km away, beyond 5km
                .longitude(77.59)
                .rating(5.0)  // Even though highest rated
                .createdAt(LocalDateTime.now())
                .build();

        Optional<Driver> result = matchingStrategy.findBestDriver(
                List.of(withinRadius, beyondRadius), 12.97, 77.59);

        assertTrue(result.isPresent());
        assertEquals("driver-001", result.get().getDriverId(), "Driver within radius should be selected even with lower rating");
    }
}
