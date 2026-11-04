package com.agricient.backend.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.agricient.backend.Model.AppUser;
import com.agricient.backend.Model.Land;
import com.agricient.backend.Repository.AppUserRepository;
import com.agricient.backend.Repository.LandRepository;

/**
 * Groups farmers and lands into the ~1 km areas used for weather. Only farmers are counted, never officers.
 * Nothing here exposes a farmer's name, login or exact pin: only counts and a place name.
 */
@Service
public class AreaService {

    /** A readable name (may be null) and how many farmers and lands are in one area. */
    public record Stats(String name, int farmers, int lands) {
    }

    private final AppUserRepository users;
    private final LandRepository lands;

    public AreaService(AppUserRepository users, LandRepository lands) {
        this.users = users;
        this.lands = lands;
    }

    public Set<Long> farmerIds() {
        Set<Long> ids = new HashSet<>();
        for (AppUser u : users.findAll()) {
            if (AppUser.FARMER.equals(u.getRole())) {
                ids.add(u.getId());
            }
        }
        return ids;
    }

    public Map<String, Stats> stats() {
        Map<String, Set<Long>> farmersByArea = new HashMap<>();
        Map<String, Integer> landsByArea = new HashMap<>();
        Map<String, String> nameByArea = new HashMap<>();
        Set<Long> farmerIds = new HashSet<>();

        for (AppUser u : users.findAll()) {
            if (!AppUser.FARMER.equals(u.getRole())) {
                continue;
            }
            farmerIds.add(u.getId());
            if (u.getLatitude() != null && u.getLongitude() != null) {
                String key = WeatherService.placeKey(u.getLatitude(), u.getLongitude());
                farmersByArea.computeIfAbsent(key, k -> new HashSet<>()).add(u.getId());
                if (u.getLocality() != null) {
                    nameByArea.putIfAbsent(key, u.getLocality());
                }
            }
        }
        for (Land l : lands.findAll()) {
            if (!farmerIds.contains(l.getOwnerId())) {
                continue;
            }
            String key = WeatherService.placeKey(l.getLatitude(), l.getLongitude());
            farmersByArea.computeIfAbsent(key, k -> new HashSet<>()).add(l.getOwnerId());
            landsByArea.merge(key, 1, Integer::sum);
            if (l.getVillage() != null) {
                nameByArea.putIfAbsent(key, l.getVillage());
            }
        }

        Map<String, Stats> out = new HashMap<>();
        for (Map.Entry<String, Set<Long>> e : farmersByArea.entrySet()) {
            out.put(e.getKey(), new Stats(nameByArea.get(e.getKey()), e.getValue().size(),
                    landsByArea.getOrDefault(e.getKey(), 0)));
        }
        return out;
    }
}
