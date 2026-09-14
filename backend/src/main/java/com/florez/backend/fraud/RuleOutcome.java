package com.florez.backend.fraud;

public record RuleOutcome(boolean triggered, RuleSeverity severity, String reason, MlScoreResponse mlScore) {

    public static RuleOutcome clean() {
        return new RuleOutcome(false, RuleSeverity.NONE, null, null);
    }

    public static RuleOutcome cleanWithScore(MlScoreResponse mlScore) {
        return new RuleOutcome(false, RuleSeverity.NONE, null, mlScore);
    }

    public static RuleOutcome of(RuleSeverity severity, String reason) {
        return new RuleOutcome(true, severity, reason, null);
    }

    public static RuleOutcome of(RuleSeverity severity, String reason, MlScoreResponse mlScore) {
        return new RuleOutcome(true, severity, reason, mlScore);
    }
}
