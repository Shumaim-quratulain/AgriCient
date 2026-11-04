package com.agricient.backend.Controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.agricient.backend.Model.WeatherReading;
import com.agricient.backend.Service.WeatherService;

@RestController
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @PostMapping("/api/weather/fetch")
    public Map<String, Object> fetch(
            @RequestParam(defaultValue = "Ludhiana") String location,
            @RequestParam(defaultValue = "30.90") double lat,
            @RequestParam(defaultValue = "75.86") double lon) {
        int saved = weatherService.fetchForecast(location, lat, lon);
        return Map.of("location", location, "saved", saved,
                "total", weatherService.count(location));
    }

    @PostMapping("/api/weather/backfill")
    public Map<String, Object> backfill(
            @RequestParam(defaultValue = "Ludhiana") String location,
            @RequestParam(defaultValue = "30.90") double lat,
            @RequestParam(defaultValue = "75.86") double lon,
            @RequestParam String from,
            @RequestParam String to) {
        int saved = weatherService.fetchArchive(location, lat, lon,
                LocalDate.parse(from), LocalDate.parse(to));
        return Map.of("location", location, "saved", saved,
                "total", weatherService.count(location));
    }

    @GetMapping("/api/weather")
    public List<WeatherReading> history(
            @RequestParam(defaultValue = "Ludhiana") String location) {
        return weatherService.history(location);
    }
}
