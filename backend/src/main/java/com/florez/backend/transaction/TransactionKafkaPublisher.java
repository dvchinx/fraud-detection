package com.florez.backend.transaction;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TransactionKafkaPublisher {

    private final KafkaTemplate<String, TransactionCreatedMessage> kafkaTemplate;

    public TransactionKafkaPublisher(KafkaTemplate<String, TransactionCreatedMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionCreated(TransactionCreatedEvent event) {
        kafkaTemplate.send(
                TransactionKafkaConfig.TRANSACTIONS_TOPIC,
                event.transactionId().toString(),
                new TransactionCreatedMessage(event.transactionId())
        );
    }
}
