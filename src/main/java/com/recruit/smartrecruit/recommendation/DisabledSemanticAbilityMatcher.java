package com.recruit.smartrecruit.recommendation;

import org.springframework.stereotype.Component;
import static com.recruit.smartrecruit.recommendation.RecommendationFeatures.AbilityFeature;
import static com.recruit.smartrecruit.recommendation.SkillMatchResult.PairMatch;
import static com.recruit.smartrecruit.recommendation.RecommendationTypes.Reason;

@Component
public class DisabledSemanticAbilityMatcher implements SemanticAbilityMatcher {
    @Override public PairMatch match(AbilityFeature requirement, AbilityFeature candidate) {
        return PairMatch.unknown(Reason.SEMANTIC_NOT_CONFIGURED);
    }
    @Override public String modelVersion() { return null; }
    @Override public String calibrationVersion() { return null; }
}
