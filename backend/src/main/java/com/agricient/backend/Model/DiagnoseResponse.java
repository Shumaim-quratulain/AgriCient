package com.agricient.backend.Model;

import java.util.List;

import com.agricient.backend.Prediction;

public record DiagnoseResponse(
        String mode,
        String crop,
        String disease,
        float confidence,
        String riskLevel,
        float safety,
        List<Prediction> top5) {
}