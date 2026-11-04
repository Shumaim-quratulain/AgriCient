package com.agricient.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.agricient.backend.Service.RiskService;

/**
 * Throwaway check, reads the real MySQL rows and writes nothing.
 * Python reference (ai-services/forecaster, ONNX, 18 Oct 2026 -> prediction for 21 Oct):
 * Tiptur 0.3959, Kozhikode 0.6433, Pollachi 0.5282.
 * Only valid while 18 Oct is the last complete day in the database.
 */
@SpringBootTest
class RiskServiceParityTest {

    @Autowired
    RiskService riskService;

    private void check(String site, double python) throws Exception {
        var f = riskService.forecastFor(site);
        System.out.println("PARITY " + site + " lastDay=" + f.lastDay()
                + " riskToday=" + f.riskToday() + " predictedFor=" + f.predictedFor()
                + " java=" + f.predictedRisk() + " python=" + python);
        assertEquals(python, f.predictedRisk(), 0.001, site);
    }

    @Test
    void tiptur() throws Exception {
        check("Tiptur", 0.3959);
    }

    @Test
    void kozhikode() throws Exception {
        check("Kozhikode", 0.6433);
    }

    @Test
    void pollachi() throws Exception {
        check("Pollachi", 0.5282);
    }
}
