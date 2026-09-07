package com.ridebooking.rideservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class InvalidRideStateException extends ResponseStatusException {
    public InvalidRideStateException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
