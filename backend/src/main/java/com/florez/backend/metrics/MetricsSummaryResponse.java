package com.florez.backend.metrics;

import java.util.Map;

public record MetricsSummaryResponse(
        long totalTransactions,
        Map<String, Long> statusCounts,
        Double avgDecisionLatencyMs,
        Double p95DecisionLatencyMs,
        Double avgMlRiskScore,
        Map<String, Long> ruleTriggerCounts,
        FraudDetectionMetrics fraudDetection
) {

    public record FraudDetectionMetrics(
            long labeledTransactions,
            long truePositives,
            long falsePositives,
            long falseNegatives,
            long trueNegatives,
            Double precision,
            Double recall
    ) {
    }
}
