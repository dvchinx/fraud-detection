package com.florez.backend.transaction;

import jakarta.validation.constraints.NotNull;

public record ConfirmFraudRequest(@NotNull Boolean confirmedFraud) {
}
