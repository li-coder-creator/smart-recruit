package com.recruit.smartrecruit.recommendation;

import java.util.*;
import static com.recruit.smartrecruit.recommendation.RecommendationTypes.Dimension;

/** 完整五项相对权重，不能把漏传或全零静默替换成默认值。 */
public record RecommendationWeights(Map<Dimension, Integer> values) {
    public enum Preset {
        BALANCED(35, 25, 20, 10, 10), SALARY_FIRST(15, 70, 5, 5, 5), CITY_FIRST(20, 10, 60, 5, 5);
        private final RecommendationWeights weights;
        Preset(int skill, int salary, int city, int experience, int education) {
            weights = new RecommendationWeights(Map.of(Dimension.SKILL, skill, Dimension.SALARY, salary,
                    Dimension.CITY, city, Dimension.EXPERIENCE, experience, Dimension.EDUCATION, education));
        }
        public RecommendationWeights weights() { return weights; }
    }
    public RecommendationWeights {
        Objects.requireNonNull(values, "weights");
        if (values.size() != Dimension.values().length) throw new IllegalArgumentException("必须提供完整五项权重");
        EnumMap<Dimension, Integer> copy = new EnumMap<>(Dimension.class);
        for (Dimension dimension : Dimension.values()) {
            Integer weight = values.get(dimension);
            if (weight == null || weight < 0 || weight > RecommendationRules.MAX_CUSTOM_WEIGHT)
                throw new IllegalArgumentException("权重必须是0～100的整数");
            copy.put(dimension, weight);
        }
        if (copy.values().stream().mapToInt(Integer::intValue).sum() == 0)
            throw new IllegalArgumentException("权重总和必须大于0");
        values = Collections.unmodifiableMap(copy);
    }
    public int get(Dimension dimension) { return values.get(dimension); }
    public int sum() { return values.values().stream().mapToInt(Integer::intValue).sum(); }
}
