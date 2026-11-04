package com.agricient.backend.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import com.agricient.backend.Model.WeatherReading;
import com.agricient.backend.Repository.WeatherRepository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
@Service
public class WeatherService {

    private static final String HOURLY =
            "temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m";

    public static final List<String> COCONUT_SITES = List.of("Tiptur", "Kozhikode", "Pollachi");

    private final WeatherRepository repository;
    private final RestClient client = RestClient.create();

    public WeatherService(WeatherRepository repository) {
        this.repository = repository;
    }

    /** One hour of Open-Meteo's response; the fields are parallel arrays. */
    public record Hourly(
            List<String> time,
            List<Double> temperature_2m,
            List<Integer> relative_humidity_2m,
            List<Double> precipitation,
            List<Double> wind_speed_10m) {
    }

    public record OpenMeteo(Hourly hourly) {
    }

    public int fetchForecast(String location, double lat, double lon) {
       OpenMeteo body = get("api.open-meteo.com", "/v1/forecast", java.util.Map.of(
        "latitude", String.valueOf(lat),
        "longitude", String.valueOf(lon),
        "hourly", HOURLY,
        "forecast_days", "3",
        "timezone", "Asia/Kolkata"));
        return store(location, lat, lon, body, null);
    }

    public int fetchRecent(String location, double lat, double lon) {
        OpenMeteo body = get("api.open-meteo.com", "/v1/forecast", java.util.Map.of(
        "latitude", String.valueOf(lat),
        "longitude", String.valueOf(lon),
        "hourly", HOURLY,
        "past_days", "7",
        "forecast_days", "1",
        "timezone", "Asia/Kolkata"));
        return store(location, lat, lon, body, LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
    }

    public int fetchArchive(String location, double lat, double lon,
            LocalDate from, LocalDate to) {
        OpenMeteo body = get("archive-api.open-meteo.com", "/v1/archive", java.util.Map.of(
        "latitude", String.valueOf(lat),
        "longitude", String.valueOf(lon),
        "hourly", HOURLY,
        "start_date", from.toString(),
        "end_date", to.toString(),
        "timezone", "Asia/Kolkata"));
        return store(location, lat, lon, body, null);
    }

    private int store(String location, double lat, double lon, OpenMeteo body, LocalDateTime upTo) {
        if (body == null || body.hourly() == null || body.hourly().time() == null) {
            return 0;
        }
        Hourly h = body.hourly();
        if (h.time().isEmpty()) {
            return 0;
        }

        LocalDateTime first = LocalDateTime.parse(h.time().get(0));
        LocalDateTime last = LocalDateTime.parse(h.time().get(h.time().size() - 1));
        Set<LocalDateTime> existing = repository
                .findByLocationAndReadingTimeBetween(location, first, last)
                .stream()
                .map(WeatherReading::getReadingTime)
                .collect(Collectors.toSet());

        List<WeatherReading> fresh = new ArrayList<>();
        for (int i = 0; i < h.time().size(); i++) {
            LocalDateTime when = LocalDateTime.parse(h.time().get(i));
            if (existing.contains(when) || (upTo != null && when.isAfter(upTo))) {
                continue;
            }
            fresh.add(new WeatherReading(location, lat, lon, when,
                    at(h.temperature_2m(), i),
                    at(h.relative_humidity_2m(), i),
                    at(h.precipitation(), i),
                    at(h.wind_speed_10m(), i)));
        }
        repository.saveAll(fresh);
        return fresh.size();
    }

    private static <T> T at(List<T> values, int i) {
        if (values == null || i >= values.size()) {
            return null;
        }
        return values.get(i);
    }

    /** Weather rows for a farmer's own place are stored under a key made from rounded coordinates. */
    public static String placeKey(double lat, double lon) {
        // 2 decimals is about 1 km, so farmers in the same spot share one stored weather history
        return String.format(java.util.Locale.ROOT, "geo:%.2f,%.2f", lat, lon);
    }

    /** Makes sure the last week of weather is stored and fresh for this place. Returns the number of new hours. */
    public synchronized int ensureRecent(String key, double lat, double lon) {
        var last = repository.findFirstByLocationOrderByReadingTimeDesc(key);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
        if (last.isPresent() && last.get().getReadingTime().isAfter(now.minusHours(3))) {
            return 0;
        }
        return fetchRecent(key, lat, lon);
    }

    public List<WeatherReading> history(String location) {
        return repository.findByLocationOrderByReadingTimeAsc(location);
    }

    public long count(String location) {
        return repository.countByLocation(location);
    }
        /** Open-Meteo answers 503 when busy; retry a few times before giving up. */
    private OpenMeteo get(String host, String path,
            java.util.Map<String, String> params) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            try {
                return client.get()
                        .uri(b -> {
                            b.scheme("https").host(host).path(path);
                            params.forEach(b::queryParam);
                            return b.build();
                        })
                        .retrieve()
                        .body(OpenMeteo.class);
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().value() < 500) {
                    throw e;                       // our mistake, don't retry
                }
                last = e;
            } catch (RuntimeException e) {
                last = e;                          // network hiccup
            }
            try {
                Thread.sleep(Duration.ofSeconds(attempt * 2L).toMillis());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while calling weather API", ie);
            }
        }
        throw new IllegalStateException("Open-Meteo unavailable after 4 attempts", last);
    }
        private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    @Scheduled(cron = "0 10 * * * *")
    public void refreshCoconutSites() {
        for (String site : COCONUT_SITES) {
            try {
                var last = repository.findFirstByLocationOrderByReadingTimeDesc(site);
                if (last.isEmpty()) {
                    log.warn("no stored reading for {}", site);
                    continue;
                }
                int n = fetchRecent(site, last.get().getLatitude(), last.get().getLongitude());
                log.info("coconut weather refresh {}: {} new hours", site, n);
            } catch (RuntimeException e) {
                log.warn("coconut weather refresh failed for {}: {}", site, e.getMessage());
            }
        }
    }
}