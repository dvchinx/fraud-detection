package com.florez.backend.fraud;

import com.florez.backend.transaction.Transaction;
import com.florez.backend.transaction.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FraudEvaluationServiceTest {

    private static final MlScoreResponse ML_SCORE = new MlScoreResponse(
            0.91, "logreg-v1", -0.94, List.of(new MlScoreResponse.FeatureContribution("amount", 2.5)));

    private static FraudRule rule(RuleOutcome outcome) {
        return transaction -> outcome;
    }

    @Test
    void noRuleTriggered_approvesAndJustifiesDecision() {
        FraudEvaluationService service = new FraudEvaluationService(List.of(rule(RuleOutcome.clean())));

        FraudEvaluationService.EvaluationResult result = service.evaluate(new Transaction());

        assertEquals(TransactionStatus.APPROVED, result.status());
        assertEquals("Ninguna regla activada", result.reason());
        assertTrue(result.triggeredRules().isEmpty());
        assertNull(result.mlScore());
    }

    @Test
    void blockSeverityWinsOverReview() {
        FraudEvaluationService service = new FraudEvaluationService(List.of(
                rule(RuleOutcome.of(RuleSeverity.REVIEW, "motivo de revisión")),
                rule(RuleOutcome.of(RuleSeverity.BLOCK, "motivo de bloqueo"))));

        FraudEvaluationService.EvaluationResult result = service.evaluate(new Transaction());

        assertEquals(TransactionStatus.BLOCKED, result.status());
        assertEquals("motivo de revisión; motivo de bloqueo", result.reason());
        assertEquals(2, result.triggeredRules().size());
    }

    @Test
    void triggeredRulesCarryNameAndSeverityForAudit() {
        Transaction transaction = new Transaction();
        transaction.setAmount(new BigDecimal("5000"));

        FraudEvaluationService service = new FraudEvaluationService(
                List.of(new HighAmountRule(new FraudRulesProperties())));

        FraudEvaluationService.EvaluationResult result = service.evaluate(transaction);

        assertEquals(TransactionStatus.REVIEW, result.status());
        assertEquals(1, result.triggeredRules().size());
        assertEquals("HighAmountRule", result.triggeredRules().get(0).rule());
        assertEquals("REVIEW", result.triggeredRules().get(0).severity());
    }

    @Test
    void mlScoreIsExposedEvenWhenTheRuleDoesNotTrigger() {
        FraudEvaluationService service = new FraudEvaluationService(List.of(
                rule(RuleOutcome.cleanWithScore(ML_SCORE))));

        FraudEvaluationService.EvaluationResult result = service.evaluate(new Transaction());

        assertEquals(TransactionStatus.APPROVED, result.status());
        assertNotNull(result.mlScore());
        assertEquals(0.91, result.mlScore().riskScore());
        assertEquals(-0.94, result.mlScore().baseValue());
    }
}
