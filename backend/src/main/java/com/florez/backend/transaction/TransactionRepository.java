package com.florez.backend.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByUserId(UUID userId);

    Optional<Transaction> findTopByUserIdAndIdNotOrderByCreatedAtDesc(UUID userId, UUID excludeId);

    @Query("SELECT t.status, COUNT(t) FROM Transaction t GROUP BY t.status")
    List<Object[]> countByStatusRaw();

    @Query(value = """
            SELECT AVG(EXTRACT(EPOCH FROM (decided_at - created_at)) * 1000)
            FROM transactions WHERE decided_at IS NOT NULL
            """, nativeQuery = true)
    Double averageDecisionLatencyMs();

    @Query(value = """
            SELECT PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (decided_at - created_at)) * 1000)
            FROM transactions WHERE decided_at IS NOT NULL
            """, nativeQuery = true)
    Double p95DecisionLatencyMs();

    @Query(value = "SELECT AVG(ml_risk_score) FROM transactions WHERE ml_risk_score IS NOT NULL", nativeQuery = true)
    Double averageMlRiskScore();

    @Query(value = """
            SELECT elem ->> 'rule' AS rule, COUNT(*) AS cnt
            FROM transactions, jsonb_array_elements(rule_outcomes) AS elem
            GROUP BY elem ->> 'rule'
            ORDER BY cnt DESC
            """, nativeQuery = true)
    List<Object[]> ruleTriggerCountsRaw();

    @Query(value = """
            SELECT
                COUNT(*) FILTER (WHERE confirmed_fraud IS NOT NULL) AS labeled,
                COUNT(*) FILTER (WHERE status IN ('REVIEW', 'BLOCKED') AND confirmed_fraud = TRUE) AS true_positives,
                COUNT(*) FILTER (WHERE status IN ('REVIEW', 'BLOCKED') AND confirmed_fraud = FALSE) AS false_positives,
                COUNT(*) FILTER (WHERE status = 'APPROVED' AND confirmed_fraud = TRUE) AS false_negatives,
                COUNT(*) FILTER (WHERE status = 'APPROVED' AND confirmed_fraud = FALSE) AS true_negatives
            FROM transactions
            """, nativeQuery = true)
    List<Object[]> fraudConfusionMatrixRaw();
}
