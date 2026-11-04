package com.agricient.backend.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agricient.backend.Model.PlaceForecast;

public interface PlaceForecastRepository extends JpaRepository<PlaceForecast, String> {
}