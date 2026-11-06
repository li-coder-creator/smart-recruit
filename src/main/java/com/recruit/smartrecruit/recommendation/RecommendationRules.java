package com.recruit.smartrecruit.recommendation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public final class RecommendationRules {
    public static final String SCORING_VERSION = "five-dimensions-v1";
    public static final BigDecimal MAX_SCORE = new BigDecimal("100");
    public static final BigDecimal MIN_SCORE = BigDecimal.ZERO;
    public static final BigDecimal EXPERIENCE_GAP_PENALTY_PER_YEAR = new BigDecimal("45");
    public static final long MONTHS_PER_YEAR = 12;
    public static final BigDecimal SALARY_MIDPOINT_DIVISOR = new BigDecimal("2");
    public static final int MAX_CUSTOM_WEIGHT = 100;
    public static final int SCORE_SCALE = 2;
    public static final int WEIGHT_RATIO_SCALE = 6;
    public static final int CONTRIBUTION_SCALE = 4;
    public static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    public static final RoundingMode DISPLAY_ROUNDING = RoundingMode.HALF_UP;
    private RecommendationRules() {}
}
