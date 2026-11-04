package com.agricient.backend.Controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.agricient.backend.Service.RiskService;
import com.agricient.backend.Service.WeatherService;

import ai.onnxruntime.OrtException;

@RestController
public class RiskController {

    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    @GetMapping("/api/risk")
    public RiskService.Forecast one(@RequestParam String site) throws OrtException, IOException {
        return riskService.forecast(site);
    }

    @GetMapping("/api/risk/coconut")
    public List<RiskService.Forecast> coconut() throws OrtException, IOException {
        List<RiskService.Forecast> out = new ArrayList<>();
        for (String site : WeatherService.COCONUT_SITES) {
            out.add(riskService.forecast(site));
        }
        return out;
    }
}
