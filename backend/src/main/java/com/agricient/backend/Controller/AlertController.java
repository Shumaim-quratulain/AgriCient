package com.agricient.backend.Controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agricient.backend.Model.Alert;
import com.agricient.backend.Service.AlertService;

import ai.onnxruntime.OrtException;

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

    @PostMapping("/api/alerts/evaluate")
    public List<Alert> evaluate() throws OrtException {
        return alertService.evaluateCoconut();
    }
}