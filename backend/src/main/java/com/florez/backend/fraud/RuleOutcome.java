package com.florez.backend.fraud;

public record RuleOutcome(boolean triggered, RuleSeverity severity, String reason) {

    public static RuleOutcome clean() {
        return new RuleOutcome(false, RuleSeverity.NONE, null);
    }

    public static RuleOutcome of(RuleSeverity severity, String reason) {
        return new RuleOutcome(true, severity, reason);
    }
}
