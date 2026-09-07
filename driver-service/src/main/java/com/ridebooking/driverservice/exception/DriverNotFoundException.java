package com.ridebooking.driverservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class DriverNotFoundException extends ResponseStatusException {
    public DriverNotFoundException(String driverId) {
        super(HttpStatus.NOT_FOUND, "Driver not found with id: " + driverId);
    }
}
