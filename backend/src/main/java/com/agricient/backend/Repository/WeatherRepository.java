package com.agricient.backend.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agricient.backend.Model.WeatherReading;

public interface WeatherRepository extends JpaRepository<WeatherReading, Long> {

    List<WeatherReading> findByLocationOrderByReadingTimeAsc(String location);

    Optional<WeatherReading> findFirstByLocationOrderByReadingTimeDesc(String location);

    List<WeatherReading> findByLocationAndReadingTimeBetween(
            String location, LocalDateTime from, LocalDateTime to);

    long countByLocation(String location);
}