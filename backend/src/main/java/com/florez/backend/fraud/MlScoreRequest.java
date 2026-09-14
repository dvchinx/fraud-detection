package com.florez.backend.fraud;

import java.math.BigDecimal;
import java.time.Instant;

public record MlScoreRequest(
        BigDecimal amount,
        String merchant,
        String country,
        Instant timestamp
) {
}
