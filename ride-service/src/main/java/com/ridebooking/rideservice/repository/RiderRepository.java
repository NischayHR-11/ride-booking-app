package com.ridebooking.rideservice.repository;

import com.ridebooking.rideservice.entity.Rider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RiderRepository extends JpaRepository<Rider, String> {
    boolean existsByPhone(String phone);
    boolean existsByEmail(String email);
}
