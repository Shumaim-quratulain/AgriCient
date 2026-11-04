package com.agricient.backend.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agricient.backend.Model.Alert;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    Optional<Alert> findBySiteAndForecastFor(String site, LocalDate forecastFor);

    List<Alert> findAllByOrderByCreatedAtDesc();
}