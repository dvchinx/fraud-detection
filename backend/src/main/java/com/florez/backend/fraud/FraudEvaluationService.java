package com.florez.backend.fraud;

import com.florez.backend.transaction.RuleTrigger;
import com.florez.backend.transaction.Transaction;
import com.florez.backend.transaction.TransactionStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FraudEvaluationService {

    private final List<FraudRule> rules;

    public FraudEvaluationService(List<FraudRule> rules) {
        this.rules = rules;
    }

    public EvaluationResult evaluate(Transaction transaction) {
        List<RuleEvaluation> evaluations = rules.stream()
                .map(rule -> new RuleEvaluation(rule.getClass().getSimpleName(), rule.evaluate(transaction)))
                .toList();

        List<RuleEvaluation> triggered = evaluations.stream()
                .filter(evaluation -> evaluation.outcome().triggered())
                .toList();

        TransactionStatus status;
        String reason;
        if (triggered.isEmpty()) {
            status = TransactionStatus.APPROVED;
            reason = "Ninguna regla activada";
        } else {
            boolean anyBlock = triggered.stream().anyMatch(evaluation -> evaluation.outcome().severity() == RuleSeverity.BLOCK);
            status = anyBlock ? TransactionStatus.BLOCKED : TransactionStatus.REVIEW;
            reason = triggered.stream().map(evaluation -> evaluation.outcome().reason()).collect(Collectors.joining("; "));
        }

        List<RuleTrigger> triggeredRules = triggered.stream()
                .map(evaluation -> new RuleTrigger(
                        evaluation.ruleName(), evaluation.outcome().severity().name(), evaluation.outcome().reason()))
                .toList();

        MlScoreResponse mlScore = evaluations.stream()
                .map(evaluation -> evaluation.outcome().mlScore())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        return new EvaluationResult(status, reason, triggeredRules, mlScore);
    }

    private record RuleEvaluation(String ruleName, RuleOutcome outcome) {
    }

    public record EvaluationResult(
            TransactionStatus status,
            String reason,
            List<RuleTrigger> triggeredRules,
            MlScoreResponse mlScore
    ) {
    }
}
