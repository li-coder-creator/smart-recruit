package com.recruit.smartrecruit.recommendation;

import java.math.BigDecimal;
import java.util.*;
import static com.recruit.smartrecruit.recommendation.RecommendationTypes.*;

public record DimensionAssessment(EvaluationState state, BigDecimal score, Reason reason,
                                  Boolean meetsRequirement, List<String> warnings) {
    public DimensionAssessment {
        Objects.requireNonNull(state); Objects.requireNonNull(reason);
        warnings = List.copyOf(warnings);
        if ((state == EvaluationState.SCORED) != (score != null))
            throw new IllegalArgumentException("仅SCORED可以并必须带分数");
        if (score != null && (score.signum() < 0 || score.compareTo(RecommendationRules.MAX_SCORE) > 0))
            throw new IllegalArgumentException("分数必须在0～100之间");
        if (state != EvaluationState.SCORED && meetsRequirement != null)
            throw new IllegalArgumentException("未知或不可比较时不能伪造达标标记");
    }
    public static DimensionAssessment scored(BigDecimal score, Reason reason, Boolean meets, List<String> warnings) {
        return new DimensionAssessment(EvaluationState.SCORED, score, reason, meets, warnings);
    }
    public static DimensionAssessment unknown(Reason reason, String warning) {
        return new DimensionAssessment(EvaluationState.UNKNOWN, null, reason, null, List.of(warning));
    }
    public static DimensionAssessment notApplicable(Reason reason, String warning) {
        return new DimensionAssessment(EvaluationState.NOT_APPLICABLE, null, reason, null, List.of(warning));
    }
}
