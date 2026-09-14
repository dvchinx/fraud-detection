package com.florez.backend.fraud;

import java.util.List;

public record MlScoreResponse(
        double riskScore,
        String modelVersion,
        List<FeatureContribution> topFactors
) {

    public record FeatureContribution(String feature, double contribution) {
    }
}
