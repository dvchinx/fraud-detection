package com.florez.backend.fraud;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "fraud.rules")
public class FraudRulesProperties {

    private BigDecimal reviewAmountThreshold = new BigDecimal("1000");
    private BigDecimal blockAmountThreshold = new BigDecimal("10000");
    private int velocityMaxCount = 5;
    private int velocityWindowSeconds = 300;

    public BigDecimal getReviewAmountThreshold() {
        return reviewAmountThreshold;
    }

    public void setReviewAmountThreshold(BigDecimal reviewAmountThreshold) {
        this.reviewAmountThreshold = reviewAmountThreshold;
    }

    public BigDecimal getBlockAmountThreshold() {
        return blockAmountThreshold;
    }

    public void setBlockAmountThreshold(BigDecimal blockAmountThreshold) {
        this.blockAmountThreshold = blockAmountThreshold;
    }

    public int getVelocityMaxCount() {
        return velocityMaxCount;
    }

    public void setVelocityMaxCount(int velocityMaxCount) {
        this.velocityMaxCount = velocityMaxCount;
    }

    public int getVelocityWindowSeconds() {
        return velocityWindowSeconds;
    }

    public void setVelocityWindowSeconds(int velocityWindowSeconds) {
        this.velocityWindowSeconds = velocityWindowSeconds;
    }
}
