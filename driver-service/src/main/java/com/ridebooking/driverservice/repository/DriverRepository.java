package com.ridebooking.driverservice.repository;

import com.ridebooking.driverservice.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DriverRepository extends JpaRepository<Driver, String> {
    List<Driver> findByAvailableTrue();
    boolean existsByPhone(String phone);
    boolean existsByVehicleNumber(String vehicleNumber);
    long countByAvailableTrue();
}
