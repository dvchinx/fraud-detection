package com.florez.backend.metrics;

import com.florez.backend.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class MetricsService {

    private final TransactionRepository transactionRepository;

    public MetricsService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public MetricsSummaryResponse summary() {
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        long total = 0;
        for (Object[] row : transactionRepository.countByStatusRaw()) {
            long count = (Long) row[1];
            statusCounts.put(row[0].toString(), count);
            total += count;
        }

        Map<String, Long> ruleTriggerCounts = new LinkedHashMap<>();
        for (Object[] row : transactionRepository.ruleTriggerCountsRaw()) {
            ruleTriggerCounts.put((String) row[0], (Long) row[1]);
        }

        Object[] confusion = transactionRepository.fraudConfusionMatrixRaw().get(0);
        long labeled = (Long) confusion[0];
        long truePositives = (Long) confusion[1];
        long falsePositives = (Long) confusion[2];
        long falseNegatives = (Long) confusion[3];
        long trueNegatives = (Long) confusion[4];

        Double precision = (truePositives + falsePositives) == 0
                ? null : (double) truePositives / (truePositives + falsePositives);
        Double recall = (truePositives + falseNegatives) == 0
                ? null : (double) truePositives / (truePositives + falseNegatives);

        MetricsSummaryResponse.FraudDetectionMetrics fraudDetection = new MetricsSummaryResponse.FraudDetectionMetrics(
                labeled, truePositives, falsePositives, falseNegatives, trueNegatives, precision, recall);

        return new MetricsSummaryResponse(
                total,
                statusCounts,
                transactionRepository.averageDecisionLatencyMs(),
                transactionRepository.p95DecisionLatencyMs(),
                transactionRepository.averageMlRiskScore(),
                ruleTriggerCounts,
                fraudDetection
        );
    }
}
