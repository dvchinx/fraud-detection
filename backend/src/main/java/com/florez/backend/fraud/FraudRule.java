package com.florez.backend.fraud;

import com.florez.backend.transaction.Transaction;

public interface FraudRule {

    RuleOutcome evaluate(Transaction transaction);
}
