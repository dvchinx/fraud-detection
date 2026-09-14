package com.florez.backend.fraud;

import java.util.List;

public record MlScoreResponse(
        double riskScore,
        String modelVersion,
        double baseValue,
        List<FeatureContribution> topFactors
) {

    public record FeatureContribution(String feature, double contribution) {
    }
}
