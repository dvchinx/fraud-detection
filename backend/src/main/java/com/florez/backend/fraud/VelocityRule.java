package com.florez.backend.fraud;

import com.florez.backend.transaction.Transaction;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class VelocityRule implements FraudRule {

    private final StringRedisTemplate redisTemplate;
    private final FraudRulesProperties properties;

    public VelocityRule(StringRedisTemplate redisTemplate, FraudRulesProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public RuleOutcome evaluate(Transaction transaction) {
        String key = "velocity:" + transaction.getUser().getId();
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(properties.getVelocityWindowSeconds()));
        }

        if (count != null && count > properties.getVelocityMaxCount()) {
            return RuleOutcome.of(RuleSeverity.REVIEW,
                    "%d transacciones en los últimos %d segundos supera el máximo de %d".formatted(
                            count, properties.getVelocityWindowSeconds(), properties.getVelocityMaxCount()));
        }
        return RuleOutcome.clean();
    }
}
