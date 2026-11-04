package com.agricient.backend.Controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agricient.backend.Model.Alert;
import com.agricient.backend.Service.AlertService;

@RestController
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping("/api/alerts")
    public List<Alert> list() {
        return alertService.all();
    }

    /** Run the rules now instead of waiting for the hourly job. */
    @PostMapping("/api/alerts/evaluate")
    public List<Alert> evaluate() throws Exception {
        return alertService.evaluateCoconut();
    }
}
