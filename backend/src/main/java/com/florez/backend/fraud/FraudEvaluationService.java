package com.florez.backend.fraud;

import com.florez.backend.transaction.Transaction;
import com.florez.backend.transaction.TransactionStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FraudEvaluationService {

    private final List<FraudRule> rules;

    public FraudEvaluationService(List<FraudRule> rules) {
        this.rules = rules;
    }

    public EvaluationResult evaluate(Transaction transaction) {
        List<RuleOutcome> triggered = rules.stream()
                .map(rule -> rule.evaluate(transaction))
                .filter(RuleOutcome::triggered)
                .toList();

        if (triggered.isEmpty()) {
            return new EvaluationResult(TransactionStatus.APPROVED, "Ninguna regla activada");
        }

        boolean anyBlock = triggered.stream().anyMatch(outcome -> outcome.severity() == RuleSeverity.BLOCK);
        TransactionStatus status = anyBlock ? TransactionStatus.BLOCKED : TransactionStatus.REVIEW;
        String reason = triggered.stream().map(RuleOutcome::reason).collect(Collectors.joining("; "));

        return new EvaluationResult(status, reason);
    }

    public record EvaluationResult(TransactionStatus status, String reason) {
    }
}
