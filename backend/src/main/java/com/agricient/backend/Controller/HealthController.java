package com.agricient.backend.Controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agricient.backend.Service.ModelService;

@RestController
public class HealthController {

    private final ModelService modelService;

    public HealthController(ModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "agricient-backend");
    }

    @GetMapping("/api/model")
    public String model() {
        return modelService.describe();
    }
}