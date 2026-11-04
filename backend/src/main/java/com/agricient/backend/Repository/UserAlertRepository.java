package com.agricient.backend.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agricient.backend.Model.UserAlert;

public interface UserAlertRepository extends JpaRepository<UserAlert, Long> {

    List<UserAlert> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<UserAlert> findByIdAndUserId(Long id, Long userId);

    Optional<UserAlert> findByUserIdAndPlaceKeyAndForecastFor(
            Long userId, String placeKey, LocalDate forecastFor);

    long countByUserIdAndReadAtIsNull(Long userId);
}