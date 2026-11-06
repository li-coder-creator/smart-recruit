package com.recruit.smartrecruit.recommendation;

import java.time.*;
import java.util.*;
import static com.recruit.smartrecruit.recommendation.WeightedScoreCalculator.Aggregate;

/** 内部结果；未来接口VO应保留分项、证据、版本及日期，不只返回一个裸总分。 */
public record RecommendationScore(Long jobId, LocalDateTime publishedAt, LocalDate asOfDate, Aggregate score,
                                  SkillMatchResult skills, Versions versions) {
    public record Versions(String scoringRuleVersion, String normalizationVersion, String aliasVersion,
                           String extractorVersion, String embeddingModelVersion, String calibrationVersion) {}
    /** 先按未舍入分数，再按发布时间及ID；null分数置末，不用显示分数决定顺序。 */
    public static Comparator<RecommendationScore> ranking() {
        return Comparator.comparing((RecommendationScore result) -> result.score().unroundedScore(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RecommendationScore::publishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RecommendationScore::jobId, Comparator.nullsLast(Comparator.reverseOrder()));
    }
}
