package com.florez.backend.fraud;

import com.florez.backend.transaction.Transaction;
import com.florez.backend.transaction.TransactionRepository;
import org.springframework.stereotype.Component;

@Component
public class GeoMismatchRule implements FraudRule {

    private final TransactionRepository transactionRepository;

    public GeoMismatchRule(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public RuleOutcome evaluate(Transaction transaction) {
        return transactionRepository
                .findTopByUserIdOrderByCreatedAtDesc(transaction.getUser().getId())
                .filter(previous -> !previous.getCountry().equals(transaction.getCountry()))
                .map(previous -> RuleOutcome.of(RuleSeverity.REVIEW,
                        "País %s difiere del país de la última transacción (%s)".formatted(
                                transaction.getCountry(), previous.getCountry())))
                .orElse(RuleOutcome.clean());
    }
}
