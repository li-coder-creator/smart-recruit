package com.recruit.smartrecruit.recommendation;

import com.recruit.smartrecruit.entity.enums.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
import static com.recruit.smartrecruit.recommendation.RecommendationFeatures.*;
import static com.recruit.smartrecruit.recommendation.RecommendationTypes.*;
import static com.recruit.smartrecruit.recommendation.RecommendationRules.*;

@Component
public class DimensionCalculator {
    public DimensionAssessment salary(Preference preference, SalaryOffer offer) {
        if (offer.unit() == null || offer.negotiable() == null)
            return DimensionAssessment.unknown(Reason.INVALID_DATA, "岗位薪资单位或面议声明未确认");
        if (offer.negotiable()) {
            if (offer.minimum() != null || offer.maximum() != null)
                return DimensionAssessment.unknown(Reason.CONFLICTING_EVIDENCE, "面议与数值薪资同时存在");
            return DimensionAssessment.notApplicable(Reason.NEGOTIABLE, "岗位明确面议，本次不比较月薪金额");
        }
        if (offer.minimum() == null || offer.maximum() == null)
            return DimensionAssessment.unknown(Reason.DATA_MISSING, "岗位薪资区间不完整");
        if (offer.minimum() <= 0 || offer.maximum() <= 0 || offer.minimum() > offer.maximum())
            return DimensionAssessment.unknown(Reason.INVALID_DATA, "岗位薪资区间无效");
        if (offer.unit() != JobSalaryUnit.MONTH)
            return DimensionAssessment.notApplicable(Reason.DIFFERENT_SALARY_UNIT, "日薪/年薪与期望月薪不可直接比较");
        if (preference.salaryMaximum() == null)
            return DimensionAssessment.unknown(Reason.DATA_MISSING, "需要确认期望月薪上限");
        if (preference.salaryMaximum() <= 0 || (preference.salaryMinimum() != null
                && (preference.salaryMinimum() <= 0 || preference.salaryMinimum() > preference.salaryMaximum())))
            return DimensionAssessment.unknown(Reason.INVALID_DATA, "期望月薪必须为正且区间有序");
        BigDecimal midpoint = BigDecimal.valueOf(offer.minimum()).add(BigDecimal.valueOf(offer.maximum())).divide(SALARY_MIDPOINT_DIVISOR);
        BigDecimal score = midpoint.multiply(MAX_SCORE).divide(BigDecimal.valueOf(preference.salaryMaximum()), CALCULATION_CONTEXT).min(MAX_SCORE);
        List<String> warnings = preference.salaryMinimum() != null && offer.maximum() < preference.salaryMinimum()
                ? List.of("岗位薪资上限低于期望下限") : List.of();
        return DimensionAssessment.scored(score, Reason.CALCULATED, null, warnings);
    }
    public DimensionAssessment city(Preference preference, JobProfile job) {
        if (preference.cityCode() == null || job.cityCode() == null)
            return DimensionAssessment.unknown(Reason.UNRECOGNIZED_VALUE, "期望或岗位城市未填写/未被精确城市表识别");
        boolean same = preference.cityCode().equals(job.cityCode());
        return DimensionAssessment.scored(same ? MAX_SCORE : MIN_SCORE, same ? Reason.REQUIREMENT_MET : Reason.REQUIREMENT_NOT_MET,
                same, same ? List.of() : List.of("岗位与期望城市不同，可自行判断是否接受异地"));
    }
    public DimensionAssessment experience(Candidate candidate, ExperienceRequirement requirement) {
        if (requirement.kind() == RequirementKind.ANY)
            return DimensionAssessment.scored(MAX_SCORE, Reason.EXPLICITLY_UNLIMITED, true, candidate.experience().warnings());
        if (requirement.kind() == RequirementKind.UNKNOWN)
            return DimensionAssessment.unknown(Reason.UNRECOGNIZED_VALUE, "岗位经验要求缺失或无法按明确规则解析");
        if (requirement.kind() == RequirementKind.STUDENT || requirement.kind() == RequirementKind.NEW_GRAD) {
            if (candidate.careerStage() == CareerStage.UNKNOWN)
                return DimensionAssessment.unknown(Reason.UNCONFIRMED_EXPERIENCE, "需要明确申报求职阶段，不能由工龄或年龄推断");
            boolean same = requirement.kind() == RequirementKind.STUDENT ? candidate.careerStage() == CareerStage.STUDENT
                    : candidate.careerStage() == CareerStage.NEW_GRAD;
            return DimensionAssessment.scored(same ? MAX_SCORE : MIN_SCORE, same ? Reason.REQUIREMENT_MET : Reason.REQUIREMENT_NOT_MET,
                    same, same ? List.of() : List.of("申报求职阶段与岗位要求不同"));
        }
        if (candidate.experience().actualMonths() == null)
            return DimensionAssessment.unknown(Reason.UNCONFIRMED_EXPERIENCE, String.join("；", candidate.experience().warnings()));
        long gap = Math.max(0L, requirement.minimumMonths() - candidate.experience().actualMonths());
        BigDecimal score = MAX_SCORE.subtract(BigDecimal.valueOf(gap).multiply(EXPERIENCE_GAP_PENALTY_PER_YEAR)
                .divide(BigDecimal.valueOf(MONTHS_PER_YEAR), CALCULATION_CONTEXT)).max(MIN_SCORE);
        return DimensionAssessment.scored(score, gap == 0 ? Reason.REQUIREMENT_MET : Reason.REQUIREMENT_NOT_MET,
                gap == 0, candidate.experience().warnings());
    }
    public DimensionAssessment education(EducationBackground background, EducationRequirement requirement) {
        if (requirement.kind() == RequirementKind.ANY)
            return DimensionAssessment.scored(MAX_SCORE, Reason.EXPLICITLY_UNLIMITED, true, background.warnings());
        if (requirement.kind() != RequirementKind.MINIMUM)
            return DimensionAssessment.unknown(Reason.UNRECOGNIZED_VALUE, "岗位学历缺失、无法识别或含未解决的毕业证明条件");
        if (background.highest() != null && background.highest().rank() >= requirement.minimum().rank())
            return DimensionAssessment.scored(MAX_SCORE, Reason.REQUIREMENT_MET, true, background.warnings());
        if (background.highest() == null || background.potentialUnknownRank() >= requirement.minimum().rank())
            return DimensionAssessment.unknown(Reason.EDUCATION_UNCERTAIN, "缺少可确认的申报学历，或未确认记录可能改变是否达标");
        return DimensionAssessment.scored(MIN_SCORE, Reason.REQUIREMENT_NOT_MET, false,
                append(background.warnings(), "岗位学历要求高于当前可确认的申报背景；这不是证书核验结论"));
    }
    private List<String> append(List<String> warnings, String message) {
        List<String> result = new ArrayList<>(warnings); result.add(message); return result;
    }
}
