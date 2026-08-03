package com.florez.backend.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByUserId(UUID userId);

    Optional<Transaction> findTopByUserIdOrderByCreatedAtDesc(UUID userId);
}
