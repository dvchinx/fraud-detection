package com.florez.backend.transaction;

import java.util.UUID;

public record TransactionCreatedEvent(UUID transactionId) {
}
