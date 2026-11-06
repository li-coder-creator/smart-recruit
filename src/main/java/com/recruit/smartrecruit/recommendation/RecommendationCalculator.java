package com.recruit.smartrecruit.recommendation;

import org.springframework.stereotype.Component;
import java.util.*;
import static com.recruit.smartrecruit.recommendation.RecommendationFeatures.*;
import static com.recruit.smartrecruit.recommendation.RecommendationTypes.*;

/** 单岗位纯计算；候选岗位资格、过滤、全局排序与分页由后续应用服务负责。 */
@Component
public class RecommendationCalculator {
    private final DimensionCalculator dimensions;
    private final SkillMatcher skills;
    private final WeightedScoreCalculator weighted;
    private final RuleDictionary dictionary;
    public RecommendationCalculator(DimensionCalculator dimensions, SkillMatcher skills,
                                    WeightedScoreCalculator weighted, RuleDictionary dictionary) {
        this.dimensions = dimensions; this.skills = skills; this.weighted = weighted; this.dictionary = dictionary;
    }
    public RecommendationScore calculate(Candidate candidate, JobProfile job, RecommendationWeights weights) {
        Objects.requireNonNull(candidate); Objects.requireNonNull(job); Objects.requireNonNull(weights);
        if (!candidate.asOfDate().equals(job.asOfDate())) throw new IllegalArgumentException("同次推荐必须使用统一asOfDate");
        SkillMatchResult skill = skills.match(job.abilities(), job.requirementsComplete(), candidate.abilities());
        EnumMap<Dimension, DimensionAssessment> assessments = new EnumMap<>(Dimension.class);
        assessments.put(Dimension.SKILL, skill.assessment());
        assessments.put(Dimension.SALARY, dimensions.salary(candidate.preference(), job.salary()));
        assessments.put(Dimension.CITY, dimensions.city(candidate.preference(), job));
        assessments.put(Dimension.EXPERIENCE, dimensions.experience(candidate, job.experience()));
        assessments.put(Dimension.EDUCATION, dimensions.education(candidate.education(), job.education()));
        SemanticAbilityMatcher semantic = skills.semanticMatcher();
        return new RecommendationScore(job.jobId(), job.publishedAt(), candidate.asOfDate(), weighted.aggregate(assessments, weights), skill,
                new RecommendationScore.Versions(RecommendationRules.SCORING_VERSION, dictionary.normalizationVersion(),
                        dictionary.aliasVersion(), dictionary.extractorVersion(), semantic.modelVersion(), semantic.calibrationVersion()));
    }
}
