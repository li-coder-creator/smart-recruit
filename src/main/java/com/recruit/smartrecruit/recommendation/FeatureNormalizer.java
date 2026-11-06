package com.recruit.smartrecruit.recommendation;

import com.recruit.smartrecruit.entity.*;
import com.recruit.smartrecruit.entity.enums.*;
import org.springframework.stereotype.Component;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import static com.recruit.smartrecruit.recommendation.RecommendationFeatures.*;
import static com.recruit.smartrecruit.recommendation.RecommendationTypes.*;

@Component
public class FeatureNormalizer {
    private static final Set<String> ANY_EXPERIENCE = Set.of("经验不限", "不限", "无经验要求", "无需经验", "0年");
    private static final Set<String> ANY_EDUCATION = Set.of("不限", "学历不限");
    private static final Set<String> NEW_GRAD = Set.of("应届生", "应届毕业生");
    private static final Pattern YEARS = Pattern.compile("^(\\d+)年(?:以上)?$");
    private static final Pattern YEAR_RANGE = Pattern.compile("^(\\d+)-(\\d+)年?$");
    private static final Map<String, EducationLevel> DEGREES = degreeAliases();
    private static Map<String, EducationLevel> degreeAliases() {
        Map<String, EducationLevel> result = new HashMap<>();
        for (String s : List.of("大专", "专科", "associate")) result.put(s, EducationLevel.ASSOCIATE);
        for (String s : List.of("本科", "学士", "bachelor", "bachelor's")) result.put(s, EducationLevel.BACHELOR);
        for (String s : List.of("硕士", "硕士研究生", "master", "master's")) result.put(s, EducationLevel.MASTER);
        for (String s : List.of("博士", "博士研究生", "doctor", "phd", "ph.d.")) result.put(s, EducationLevel.DOCTOR);
        return Map.copyOf(result);
    }
    public ExperienceRequirement experienceRequirement(String raw) {
        String value = RuleDictionary.token(raw).replaceAll("(?U)\\s+", "")
                .replace('–', '-').replace('—', '-').replace('−', '-');
        if (ANY_EXPERIENCE.contains(value)) return new ExperienceRequirement(raw, RequirementKind.ANY, 0L, null);
        if (value.equals("在校生")) return new ExperienceRequirement(raw, RequirementKind.STUDENT, null, null);
        if (NEW_GRAD.contains(value)) return new ExperienceRequirement(raw, RequirementKind.NEW_GRAD, null, null);
        var range = YEAR_RANGE.matcher(value); var years = YEARS.matcher(value);
        try {
            if (range.matches()) {
                long minimum = Long.parseLong(range.group(1)), upper = Long.parseLong(range.group(2));
                if (minimum > 0 && upper >= minimum) return new ExperienceRequirement(raw, RequirementKind.MINIMUM,
                        Math.multiplyExact(minimum, RecommendationRules.MONTHS_PER_YEAR), Math.multiplyExact(upper, RecommendationRules.MONTHS_PER_YEAR));
            } else if (years.matches()) {
                long minimum = Long.parseLong(years.group(1));
                if (minimum > 0) return new ExperienceRequirement(raw, RequirementKind.MINIMUM,
                        Math.multiplyExact(minimum, RecommendationRules.MONTHS_PER_YEAR), null);
            }
        } catch (ArithmeticException | NumberFormatException ignored) {
            // 历史文本超出可计算范围，作为未知保留原文，不让单个异常字段中断整批推荐。
        }
        return new ExperienceRequirement(raw, RequirementKind.UNKNOWN, null, null);
    }
    public EducationRequirement educationRequirement(String raw) {
        String value = RuleDictionary.token(raw);
        if (ANY_EDUCATION.contains(value)) return new EducationRequirement(raw, RequirementKind.ANY, null);
        String minimum = value.replaceFirst("(?:及以上|以上)$", "").strip();
        EducationLevel level = DEGREES.get(minimum);
        return new EducationRequirement(raw, level == null ? RequirementKind.UNKNOWN : RequirementKind.MINIMUM, level);
    }
    public FormalExperience formalExperience(FormalExperienceStatus declaration,
                                             List<ResumeExperience> records, LocalDate asOfDate) {
        Objects.requireNonNull(records, "必须先加载经历列表"); Objects.requireNonNull(asOfDate);
        List<String> warnings = new ArrayList<>(); List<DateInterval> intervals = new ArrayList<>();
        boolean unknown = false, hasFormal = false;
        for (ResumeExperience record : records) {
            Objects.requireNonNull(record, "经历条目不能为null");
            ExperienceType type = record.getExperienceType();
            if (type == null || type == ExperienceType.UNKNOWN) {
                unknown = true; warnings.add("存在未确认类型的工作经历，可能影响正式工龄"); continue;
            }
            if (type != ExperienceType.FORMAL) continue;
            hasFormal = true;
            LocalDate start = record.getStartDate(), end = record.getEndDate();
            boolean current = Boolean.TRUE.equals(record.getIsCurrent());
            if (start == null || start.isAfter(asOfDate) || record.getIsCurrent() == null
                    || (current && end != null) || (!current && end == null)) {
                unknown = true; warnings.add("正式经历的日期或至今标记未确认/矛盾"); continue;
            }
            if (current) end = asOfDate;
            if (end.isAfter(asOfDate) || end.isBefore(start) || end.equals(LocalDate.MAX)) {
                unknown = true; warnings.add("正式经历的结束日期无效"); continue;
            }
            intervals.add(new DateInterval(start, end.plusDays(1)));
        }
        intervals.sort(Comparator.comparing(DateInterval::start));
        List<DateInterval> merged = new ArrayList<>();
        for (DateInterval interval : intervals) {
            if (merged.isEmpty() || interval.start().isAfter(merged.get(merged.size() - 1).endExclusive())) {
                merged.add(interval);
            } else {
                DateInterval previous = merged.remove(merged.size() - 1);
                merged.add(new DateInterval(previous.start(), previous.endExclusive().isAfter(interval.endExclusive())
                        ? previous.endExclusive() : interval.endExclusive()));
            }
        }
        long months = 0;
        for (DateInterval interval : merged) months = Math.addExact(months, ChronoUnit.MONTHS.between(interval.start(), interval.endExclusive()));
        if (declaration == FormalExperienceStatus.NONE && (hasFormal || unknown)) {
            unknown = true; warnings.add("无正式经验声明与正式/未分类经历冲突");
        }
        Long actual = !unknown && (hasFormal || declaration == FormalExperienceStatus.NONE) ? months : null;
        if (actual == null && !unknown) warnings.add("未确认无正式经验；空经历或仅实习不能自动算作0个月");
        return new FormalExperience(actual, months, unknown, merged, warnings);
    }
    public EducationBackground education(List<ResumeEducation> records, LocalDate asOfDate) {
        Objects.requireNonNull(records, "必须先加载教育列表"); Objects.requireNonNull(asOfDate);
        List<EducationEvidence> evidence = new ArrayList<>(); List<String> warnings = new ArrayList<>();
        EducationLevel highest = null; int uncertainRank = 0;
        for (ResumeEducation record : records) {
            Objects.requireNonNull(record, "教育条目不能为null");
            EducationLevel level = DEGREES.get(RuleDictionary.token(record.getDegree()));
            LocalDate start = record.getStartDate(), end = record.getEndDate();
            boolean inverted = start != null && end != null && end.isBefore(start);
            if (!inverted && start != null && start.isAfter(asOfDate)) {
                evidence.add(new EducationEvidence(record.getDegree(), level, StudyStage.PLANNED));
                warnings.add("未来计划教育未计入申报背景"); continue;
            }
            if (level == null || start == null || inverted) {
                uncertainRank = Math.max(uncertainRank, level == null ? EducationLevel.DOCTOR.rank() : level.rank());
                evidence.add(new EducationEvidence(record.getDegree(), level, StudyStage.INVALID));
                warnings.add("存在学历名称或日期未确认的教育记录"); continue;
            }
            if (highest == null || level.rank() > highest.rank()) highest = level;
            StudyStage stage = end == null ? StudyStage.STAGE_UNKNOWN
                    : end.isAfter(asOfDate) ? StudyStage.IN_PROGRESS : StudyStage.END_DATE_PASSED;
            evidence.add(new EducationEvidence(record.getDegree(), level, stage));
        }
        return new EducationBackground(highest, uncertainRank, evidence, warnings);
    }
}
