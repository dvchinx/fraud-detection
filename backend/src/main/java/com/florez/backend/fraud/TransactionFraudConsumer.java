package com.florez.backend.fraud;

import com.florez.backend.transaction.Transaction;
import com.florez.backend.transaction.TransactionCreatedMessage;
import com.florez.backend.transaction.TransactionKafkaConfig;
import com.florez.backend.transaction.TransactionRepository;
import com.florez.backend.transaction.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionFraudConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionFraudConsumer.class);

    private final TransactionRepository transactionRepository;
    private final FraudEvaluationService fraudEvaluationService;

    public TransactionFraudConsumer(
            TransactionRepository transactionRepository,
            FraudEvaluationService fraudEvaluationService
    ) {
        this.transactionRepository = transactionRepository;
        this.fraudEvaluationService = fraudEvaluationService;
    }

    @KafkaListener(topics = TransactionKafkaConfig.TRANSACTIONS_TOPIC)
    @Transactional
    public void onMessage(TransactionCreatedMessage message) {
        Transaction transaction = transactionRepository.findById(message.transactionId()).orElse(null);

        if (transaction == null) {
            log.error("Transaccion {} no encontrada al procesar evento de Kafka; se descarta", message.transactionId());
            return;
        }

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            log.debug("Transaccion {} ya evaluada (status={}), se ignora reentrega",
                    transaction.getId(), transaction.getStatus());
            return;
        }

        FraudEvaluationService.EvaluationResult evaluation = fraudEvaluationService.evaluate(transaction);
        transaction.setStatus(evaluation.status());
        transaction.setReason(evaluation.reason());
        transactionRepository.save(transaction);
    }
}
