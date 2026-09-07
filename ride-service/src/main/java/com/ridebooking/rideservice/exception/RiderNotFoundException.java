package com.ridebooking.rideservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exception thrown when a requested rider is not found in the system.
 * Returns HTTP 404 Not Found status to the client.
 */
public class RiderNotFoundException extends ResponseStatusException {
    /**
     * Constructs a RiderNotFoundException with the given rider ID.
     *
     * @param riderId the ID of the rider that was not found
     */
    public RiderNotFoundException(String riderId) {
        super(HttpStatus.NOT_FOUND, "Rider not found with id: " + riderId);
    }
}
