package com.florez.backend.transaction;

import com.florez.backend.common.exception.ResourceNotFoundException;
import com.florez.backend.user.User;
import com.florez.backend.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TransactionService(
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + request.userId()));

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setAmount(request.amount());
        transaction.setCurrency(request.currency());
        transaction.setMerchant(request.merchant());
        transaction.setCountry(request.country());

        Transaction saved = transactionRepository.save(transaction);
        eventPublisher.publishEvent(new TransactionCreatedEvent(saved.getId()));

        return TransactionResponse.from(saved);
    }

    public TransactionResponse getById(UUID id) {
        return transactionRepository.findById(id)
                .map(TransactionResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Transacción no encontrada: " + id));
    }

    public List<TransactionResponse> getAll(UUID userId) {
        List<Transaction> transactions = userId != null
                ? transactionRepository.findByUserId(userId)
                : transactionRepository.findAll();

        return transactions.stream()
                .map(TransactionResponse::from)
                .toList();
    }
}
