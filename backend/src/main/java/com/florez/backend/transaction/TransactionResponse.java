package com.florez.backend.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID userId,
        BigDecimal amount,
        String currency,
        String merchant,
        String country,
        TransactionStatus status,
        String reason,
        Instant createdAt,
        Instant decidedAt,
        List<RuleTrigger> ruleOutcomes,
        Double mlRiskScore,
        String mlModelVersion,
        Double mlBaseValue,
        List<FeatureContribution> mlTopFactors,
        Boolean confirmedFraud
) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getUser().getId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getMerchant(),
                transaction.getCountry(),
                transaction.getStatus(),
                transaction.getReason(),
                transaction.getCreatedAt(),
                transaction.getDecidedAt(),
                transaction.getRuleOutcomes(),
                transaction.getMlRiskScore(),
                transaction.getMlModelVersion(),
                transaction.getMlBaseValue(),
                transaction.getMlTopFactors(),
                transaction.getConfirmedFraud()
        );
    }
}
