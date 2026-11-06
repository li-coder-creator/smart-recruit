package com.recruit.smartrecruit.recommendation;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
import static com.recruit.smartrecruit.recommendation.RecommendationTypes.*;
import static com.recruit.smartrecruit.recommendation.RecommendationRules.*;

@Component
public class WeightedScoreCalculator {
    public record WeightedDimension(DimensionAssessment assessment, int requestedWeight, boolean ignored,
                                    BigDecimal effectiveWeightRatio, BigDecimal contribution) {}
    public record Aggregate(BigDecimal totalScore, BigDecimal unroundedScore, BigDecimal dataCoverage,
                            BigDecimal excludedWeight, Reason reason, Map<Dimension, WeightedDimension> dimensions) {
        public Aggregate { dimensions = Collections.unmodifiableMap(new EnumMap<>(dimensions)); }
    }
    public Aggregate aggregate(Map<Dimension, DimensionAssessment> assessments, RecommendationWeights weights) {
        Objects.requireNonNull(assessments); Objects.requireNonNull(weights);
        if (assessments.size() != Dimension.values().length) throw new IllegalArgumentException("必须提供完整五维结果");
        int denominator = 0, covered = 0, excluded = 0;
        BigDecimal numerator = BigDecimal.ZERO;
        for (Dimension dimension : Dimension.values()) {
            DimensionAssessment assessment = Objects.requireNonNull(assessments.get(dimension));
            int weight = weights.get(dimension);
            if (assessment.state() == EvaluationState.NOT_APPLICABLE) { excluded += weight; continue; }
            denominator += weight;
            if (assessment.state() == EvaluationState.SCORED) {
                covered += weight; numerator = numerator.add(assessment.score().multiply(BigDecimal.valueOf(weight)));
            }
        }
        EnumMap<Dimension, WeightedDimension> details = new EnumMap<>(Dimension.class);
        for (Dimension dimension : Dimension.values()) {
            DimensionAssessment assessment = assessments.get(dimension);
            int weight = weights.get(dimension);
            BigDecimal ratio = denominator == 0 || assessment.state() == EvaluationState.NOT_APPLICABLE ? BigDecimal.ZERO
                    : BigDecimal.valueOf(weight).divide(BigDecimal.valueOf(denominator), CALCULATION_CONTEXT);
            // 贡献直接由原始分数和整数权重计算，不使用已舍入的展示比例。
            BigDecimal contribution = denominator == 0 || assessment.state() != EvaluationState.SCORED ? BigDecimal.ZERO
                    : assessment.score().multiply(BigDecimal.valueOf(weight)).divide(BigDecimal.valueOf(denominator), CALCULATION_CONTEXT);
            details.put(dimension, new WeightedDimension(assessment, weight, weight == 0,
                    ratio.setScale(WEIGHT_RATIO_SCALE, DISPLAY_ROUNDING), contribution.setScale(CONTRIBUTION_SCALE, DISPLAY_ROUNDING)));
        }
        Reason reason = denominator == 0 ? Reason.NO_COMPARABLE_DIMENSIONS
                : weights.get(Dimension.SKILL) > 0 && assessments.get(Dimension.SKILL).state() == EvaluationState.UNKNOWN ? Reason.CORE_SKILLS_UNKNOWN
                : covered == 0 ? Reason.NO_SCORED_DIMENSIONS : Reason.CALCULATED;
        BigDecimal raw = reason == Reason.CALCULATED ? numerator.divide(BigDecimal.valueOf(denominator), CALCULATION_CONTEXT) : null;
        return new Aggregate(raw == null ? null : raw.setScale(SCORE_SCALE, DISPLAY_ROUNDING), raw,
                denominator == 0 ? BigDecimal.ZERO.setScale(SCORE_SCALE) : percent(covered, denominator), percent(excluded, weights.sum()), reason, details);
    }
    private BigDecimal percent(int numerator, int denominator) {
        return BigDecimal.valueOf(numerator).multiply(MAX_SCORE).divide(BigDecimal.valueOf(denominator), SCORE_SCALE, DISPLAY_ROUNDING);
    }
}
