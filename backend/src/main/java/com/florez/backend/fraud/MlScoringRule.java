package com.florez.backend.fraud;

import com.florez.backend.transaction.Transaction;
import org.springframework.stereotype.Component;

@Component
public class MlScoringRule implements FraudRule {

    private final MlServiceClient mlServiceClient;
    private final FraudRulesProperties properties;

    public MlScoringRule(MlServiceClient mlServiceClient, FraudRulesProperties properties) {
        this.mlServiceClient = mlServiceClient;
        this.properties = properties;
    }

    @Override
    public RuleOutcome evaluate(Transaction transaction) {
        return mlServiceClient.score(transaction)
                .map(this::toOutcome)
                .orElse(RuleOutcome.clean());
    }

    private RuleOutcome toOutcome(MlScoreResponse score) {
        if (score.riskScore() >= properties.getMlBlockThreshold()) {
            return RuleOutcome.of(RuleSeverity.BLOCK,
                    "Modelo ML (%s) score=%.2f supera el umbral de bloqueo %.2f".formatted(
                            score.modelVersion(), score.riskScore(), properties.getMlBlockThreshold()),
                    score);
        }
        if (score.riskScore() >= properties.getMlReviewThreshold()) {
            return RuleOutcome.of(RuleSeverity.REVIEW,
                    "Modelo ML (%s) score=%.2f supera el umbral de revisión %.2f".formatted(
                            score.modelVersion(), score.riskScore(), properties.getMlReviewThreshold()),
                    score);
        }
        return RuleOutcome.cleanWithScore(score);
    }
}
