package com.agricient.backend.Model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_weather_location_time",
        columnNames = { "location", "readingTime" }))
public class WeatherReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable name for the place, e.g. "Ludhiana". */
    @Column(nullable = false, length = 80)
    private String location;

    private double latitude;
    private double longitude;

    /** The hour this reading describes. */
    @Column(name = "readingTime", nullable = false)
    private LocalDateTime readingTime;

    /** Degrees Celsius. Boxed so a missing value is null, not a fake 0. */
    private Double temperature;

    /** Relative humidity, percent. */
    private Integer humidity;

    /** Rain in mm for that hour. */
    private Double precipitation;

    /** Wind speed in km/h. */
    private Double windSpeed;

    public WeatherReading() {
    }

    public WeatherReading(String location, double latitude, double longitude,
            LocalDateTime readingTime, Double temperature, Integer humidity,
            Double precipitation, Double windSpeed) {
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
        this.readingTime = readingTime;
        this.temperature = temperature;
        this.humidity = humidity;
        this.precipitation = precipitation;
        this.windSpeed = windSpeed;
    }

    public Long getId() {
        return id;
    }

    public String getLocation() {
        return location;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public LocalDateTime getReadingTime() {
        return readingTime;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Integer getHumidity() {
        return humidity;
    }

    public Double getPrecipitation() {
        return precipitation;
    }

    public Double getWindSpeed() {
        return windSpeed;
    }
}