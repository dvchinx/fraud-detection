package com.florez.backend.transaction;

import java.util.UUID;

public record TransactionCreatedMessage(UUID transactionId) {
}
