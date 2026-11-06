package com.recruit.smartrecruit.recommendation;

import java.math.BigDecimal;
import java.util.*;
import static com.recruit.smartrecruit.recommendation.RecommendationFeatures.AbilityFeature;
import static com.recruit.smartrecruit.recommendation.RecommendationTypes.*;

public record SkillMatchResult(DimensionAssessment assessment, List<RequirementMatch> requirements) {
    public SkillMatchResult { Objects.requireNonNull(assessment); requirements = List.copyOf(requirements); }
    public record PairMatch(MatchMethod method, MatchDecision decision, BigDecimal score, BigDecimal similarity, Reason reason) {
        public PairMatch {
            Objects.requireNonNull(method); Objects.requireNonNull(decision); Objects.requireNonNull(reason);
            if ((decision == MatchDecision.UNKNOWN) != (score == null)) throw new IllegalArgumentException("未知匹配只能返回null分数");
            if (score != null && (score.signum() < 0 || score.compareTo(RecommendationRules.MAX_SCORE) > 0))
                throw new IllegalArgumentException("匹配分数超出范围");
            if (score != null && ((decision == MatchDecision.MATCH && score.compareTo(RecommendationRules.MAX_SCORE) != 0)
                    || (decision == MatchDecision.NO_MATCH && score.signum() != 0)
                    || (decision == MatchDecision.PARTIAL && (score.signum() == 0 || score.compareTo(RecommendationRules.MAX_SCORE) == 0))))
                throw new IllegalArgumentException("匹配判断与分数矛盾");
            if (similarity != null && similarity.abs().compareTo(BigDecimal.ONE) > 0)
                throw new IllegalArgumentException("相似度必须在-1～1之间");
        }
        public static PairMatch unknown(Reason reason) { return new PairMatch(MatchMethod.SEMANTIC, MatchDecision.UNKNOWN, null, null, reason); }
    }
    public record RequirementMatch(List<AbilityFeature> equivalentRequirements, AbilityFeature candidateEvidence, PairMatch match) {
        public RequirementMatch { equivalentRequirements = List.copyOf(equivalentRequirements); Objects.requireNonNull(match); }
    }
}
