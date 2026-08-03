package com.florez.backend.fraud;

import com.florez.backend.transaction.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class HighAmountRule implements FraudRule {

    private final FraudRulesProperties properties;

    public HighAmountRule(FraudRulesProperties properties) {
        this.properties = properties;
    }

    @Override
    public RuleOutcome evaluate(Transaction transaction) {
        BigDecimal amount = transaction.getAmount();

        if (amount.compareTo(properties.getBlockAmountThreshold()) > 0) {
            return RuleOutcome.of(RuleSeverity.BLOCK,
                    "Monto %s supera el umbral de bloqueo %s".formatted(amount, properties.getBlockAmountThreshold()));
        }
        if (amount.compareTo(properties.getReviewAmountThreshold()) > 0) {
            return RuleOutcome.of(RuleSeverity.REVIEW,
                    "Monto %s supera el umbral de revisión %s".formatted(amount, properties.getReviewAmountThreshold()));
        }
        return RuleOutcome.clean();
    }
}
