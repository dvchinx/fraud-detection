package com.florez.backend.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID userId,
        BigDecimal amount,
        String currency,
        String merchant,
        String country,
        TransactionStatus status,
        Instant createdAt
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
                transaction.getCreatedAt()
        );
    }
}
