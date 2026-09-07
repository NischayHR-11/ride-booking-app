package com.ridebooking.driverservice.service;

import com.ridebooking.driverservice.dto.DriverRegisterDto;
import com.ridebooking.driverservice.dto.DriverResponseDto;
import com.ridebooking.driverservice.dto.LocationUpdateDto;
import com.ridebooking.driverservice.entity.Driver;
import com.ridebooking.driverservice.events.DriverAssignedEvent;
import com.ridebooking.driverservice.events.DriverEventPublisher;
import com.ridebooking.driverservice.events.NoDriverAvailableEvent;
import com.ridebooking.driverservice.events.RideRequestedEventPayload;
import com.ridebooking.driverservice.exception.DriverNotFoundException;
import com.ridebooking.driverservice.repository.DriverRepository;
import in.zeta.spectra.capture.SpectraLogger;
import lombok.RequiredArgsConstructor;
import olympus.trace.OlympusSpectra;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/*
 * Design Patterns used in this service:
 *
 * 1. DEPENDENCY INJECTION (Constructor Injection via @RequiredArgsConstructor)
 *    — Spring wires DriverRepository, DriverEventPublisher, and the active
 *      DriverMatchingStrategy implementation at startup.
 *
 * 2. STRATEGY (DriverMatchingStrategy)
 *    — The matching algorithm is injected as an interface, not a concrete class.
 *      Swap NearestDriverMatchingStrategy for any other implementation without
 *      touching this service.
 *
 * 3. REPOSITORY (DriverRepository extends JpaRepository)
 *    — All persistence is handled through the repository abstraction; no raw
 *      SQL or EntityManager calls appear here.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DriverService {

    private static final SpectraLogger logger = OlympusSpectra.getLogger(DriverService.class);
    private static final String DRIVER_ID_ATTR = "driverId";

    // [Repository Pattern] Persistence abstracted behind JpaRepository
    private final DriverRepository driverRepository;
    private final DriverEventPublisher driverEventPublisher;
    // [Strategy Pattern] Active matching strategy injected by Spring DI
    private final DriverMatchingStrategy matchingStrategy;

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

    public DriverResponseDto updateAvailability(String driverId, boolean available) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new DriverNotFoundException(driverId));
        driver.setAvailable(available);
        driverRepository.save(driver);
        logger.info("[DriverService] Availability updated")
                .attr(DRIVER_ID_ATTR, driverId)
                .attr("available", String.valueOf(available))
                .log();
        return mapToResponse(driver);
    }

    public DriverResponseDto updateLocation(String driverId, LocationUpdateDto dto) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new DriverNotFoundException(driverId));
        driver.setLatitude(dto.getLatitude());
        driver.setLongitude(dto.getLongitude());
        driverRepository.save(driver);
        return mapToResponse(driver);
    }

    /**
     * Consumes RIDE_REQUESTED event.
     * Runs the driver matching strategy and publishes either DRIVER_ASSIGNED or NO_DRIVER_AVAILABLE.
     */
    public void handleRideRequested(RideRequestedEventPayload payload) {
        logger.info("[DriverService] Received RIDE_REQUESTED event")
                .attr("rideId", payload.getRideId())
                .attr("pickup", payload.getPickupLocation())
                .log();

        List<Driver> availableDrivers = driverRepository.findByAvailableTrue();

        // Use pickup coordinates from the event for proximity-based matching
        Optional<Driver> matched = matchingStrategy.findBestDriver(
                availableDrivers, payload.getPickupLat(), payload.getPickupLng());

        if (matched.isEmpty()) {
            logger.warn("[DriverService] No driver available for ride: " + payload.getRideId()).log();
            driverEventPublisher.publishNoDriverAvailable(
                    NoDriverAvailableEvent.of(payload.getRideId(), "No available drivers in the system")
            );
            return;
        }

        Driver driver = matched.get();
        driver.setAvailable(false);
        driverRepository.save(driver);

        logger.info("[DriverService] Driver assigned")
                .attr(DRIVER_ID_ATTR, driver.getDriverId())
                .attr("rideId", payload.getRideId())
                .log();

        driverEventPublisher.publishDriverAssigned(
                DriverAssignedEvent.of(payload.getRideId(), driver.getDriverId(),
                        driver.getVehicleNumber(), 10)
        );
    }

    @Transactional(readOnly = true)
    public DriverResponseDto getDriver(String driverId) {
        return mapToResponse(driverRepository.findById(driverId)
                .orElseThrow(() -> new DriverNotFoundException(driverId)));
    }

    @Transactional(readOnly = true)
    public List<DriverResponseDto> getAllDrivers() {
        return driverRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DriverResponseDto> getAvailableDrivers() {
        return driverRepository.findByAvailableTrue()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDriverStats(String driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new DriverNotFoundException(driverId));
        
        return Map.of(
                DRIVER_ID_ATTR, driver.getDriverId(),
                "name", driver.getName(),
                "vehicleType", driver.getVehicleType(),
                "vehicleNumber", driver.getVehicleNumber(),
                "rating", driver.getRating(),
                "available", driver.isAvailable(),
                "totalAvailableDrivers", driverRepository.countByAvailableTrue()
        );
    }

    private DriverResponseDto mapToResponse(Driver driver) {
        return DriverResponseDto.builder()
                .driverId(driver.getDriverId())
                .name(driver.getName())
                .phone(driver.getPhone())
                .vehicleNumber(driver.getVehicleNumber())
                .vehicleType(driver.getVehicleType())
                .latitude(driver.getLatitude())
                .longitude(driver.getLongitude())
                .available(driver.isAvailable())
                .rating(driver.getRating())
                .createdAt(driver.getCreatedAt())
                .build();
    }
}
