package com.ridebooking.rideservice.service;

import com.ridebooking.rideservice.dto.RiderRequestDto;
import com.ridebooking.rideservice.dto.RiderResponseDto;
import com.ridebooking.rideservice.entity.Rider;
import com.ridebooking.rideservice.exception.RiderNotFoundException;
import com.ridebooking.rideservice.repository.RiderRepository;
import in.zeta.spectra.capture.SpectraLogger;
import lombok.RequiredArgsConstructor;
import olympus.trace.OlympusSpectra;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Service for managing rider operations including registration and retrieval.
 * Handles business logic for rider management, validation, and persistence.
 * All operations are transactional and logged.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RiderService {

    private static final SpectraLogger logger = OlympusSpectra.getLogger(RiderService.class);

    private final RiderRepository riderRepository;

    /**
     * Register a new rider with validation for unique phone and email.
     *
     * @param request the rider registration request containing name, phone, and email
     * @return the registered rider details as a response DTO
     * @throws ResponseStatusException if phone or email is already registered
     */
    public RiderResponseDto registerRider(RiderRequestDto request) {
        if (riderRepository.existsByPhone(request.getPhone())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number already registered: " + request.getPhone());
        }
        if (riderRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered: " + request.getEmail());
        }

        Rider rider = Rider.builder()
                .name(request.getName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .build();

        rider = riderRepository.save(rider);

        logger.info("[RiderService] Rider registered")
                .attr("riderId", rider.getRiderId())
                .attr("name", rider.getName())
                .log();

        return mapToResponse(rider);
    }

    /**
     * Retrieve all registered riders.
     *
     * @return list of all rider response DTOs
     */
    @Transactional(readOnly = true)
    public List<RiderResponseDto> getAllRiders() {
        return riderRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RiderResponseDto getRider(String riderId) {
        Rider rider = riderRepository.findById(riderId)
                .orElseThrow(() -> new RiderNotFoundException(riderId));
        return mapToResponse(rider);
    }

    /**
     * Map Rider entity to RiderResponseDto.
     *
     * @param rider the rider entity to map
     * @return the mapped rider response DTO
     */
    private RiderResponseDto mapToResponse(Rider rider) {
        return RiderResponseDto.builder()
                .riderId(rider.getRiderId())
                .name(rider.getName())
                .phone(rider.getPhone())
                .email(rider.getEmail())
                .role(rider.getRole())
                .createdAt(rider.getCreatedAt())
                .build();
    }
}
