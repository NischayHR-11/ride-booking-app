package com.ridebooking.rideservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class RideNotFoundException extends ResponseStatusException {
    public RideNotFoundException(String rideId) {
        super(HttpStatus.NOT_FOUND, "Ride not found with id: " + rideId);
    }
}
