package com.recruit.smartrecruit.recommendation;

import static com.recruit.smartrecruit.recommendation.RecommendationFeatures.AbilityFeature;
import static com.recruit.smartrecruit.recommendation.SkillMatchResult.PairMatch;

/** 语义扩展边界。真实实现必须固定模型、校准版本；不能将相似度直接当分数。 */
public interface SemanticAbilityMatcher {
    PairMatch match(AbilityFeature requirement, AbilityFeature candidate);
    String modelVersion();
    String calibrationVersion();
}
