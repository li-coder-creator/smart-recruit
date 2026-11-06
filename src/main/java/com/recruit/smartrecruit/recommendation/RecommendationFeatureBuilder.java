package com.recruit.smartrecruit.recommendation;

import com.recruit.smartrecruit.entity.*;
import com.recruit.smartrecruit.entity.enums.*;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.*;
import static com.recruit.smartrecruit.recommendation.RecommendationFeatures.*;

/** 调用方先完成鉴权并在一致读取快照中加载实体；本类不查库、不写库。 */
@Component
public class RecommendationFeatureBuilder {
    private final RuleDictionary dictionary;
    private final FeatureNormalizer normalizer;
    private final AbilityFeatureExtractor extractor;
    public RecommendationFeatureBuilder(RuleDictionary dictionary, FeatureNormalizer normalizer, AbilityFeatureExtractor extractor) {
        this.dictionary = dictionary; this.normalizer = normalizer; this.extractor = extractor;
    }
    public Candidate candidate(Resumebasic resume, JobPreference preference, List<ResumeEducation> education,
                               List<ResumeExperience> experience, List<ResumeSkill> skills, LocalDate asOfDate) {
        Objects.requireNonNull(resume); Objects.requireNonNull(asOfDate); Objects.requireNonNull(skills, "必须先加载技能列表");
        List<String> warnings = new ArrayList<>();
        Preference intent = preference == null ? new Preference(null, null, null, null, null, null)
                : new Preference(preference.getExpectedPosition(), direction(preference.getDirectionCode(), preference.getExpectedPosition(), warnings),
                    preference.getExpectedCity(), dictionary.city(preference.getExpectedCity()), preference.getSalaryMin(), preference.getSalaryMax());
        List<AbilityFeature> abilities = skills.stream().map(skill -> extractor.extract("resume_skill:" + skill.getId(),
                skill.getSkillName(), skill.getDescription(), null, skill.getProficiency(), false)).toList();
        return new Candidate(resume.getId(), asOfDate, resume.getCareerStage() == null ? CareerStage.UNKNOWN : resume.getCareerStage(), intent,
                normalizer.formalExperience(resume.getFormalExperienceStatus(), experience, asOfDate),
                normalizer.education(education, asOfDate), abilities, warnings);
    }
    public JobProfile job(Job job, List<JobRequiredAbility> structured, LocalDate asOfDate) {
        Objects.requireNonNull(job); Objects.requireNonNull(asOfDate);
        List<String> warnings = new ArrayList<>(); List<AbilityFeature> requirements;
        boolean complete = true;
        if (job.getRequiredAbilitiesSource() == RequiredAbilitiesSource.STRUCTURED) {
            Objects.requireNonNull(structured, "STRUCTURED必须实际加载能力子表，不能回退旧文本");
            requirements = structured.stream().sorted(Comparator.comparing(JobRequiredAbility::getSortOrder,
                            Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(JobRequiredAbility::getId,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(item -> extractor.extract("job_required_ability:" + item.getId(), item.getRawText(), null,
                            item.getAbilityName(), item.getProficiency(), true)).toList();
        } else if (job.getRequiredAbilitiesSource() == RequiredAbilitiesSource.LEGACY_TEXT) {
            requirements = extractor.legacyRequirements(job.getRequiredSkills());
        } else {
            requirements = List.of(); complete = false; warnings.add("能力来源缺失或未知，未猜测为旧文本模式");
        }
        return new JobProfile(job.getId(), asOfDate, job.getPublishedAt(), direction(job.getDirectionCode(), job.getCategory(), warnings),
                job.getCity(), dictionary.city(job.getCity()), new SalaryOffer(job.getSalaryMin(), job.getSalaryMax(), job.getSalaryUnit(), job.getNegotiable()),
                normalizer.experienceRequirement(job.getExperience()), normalizer.educationRequirement(job.getEducation()),
                job.getRequiredAbilitiesSource(), job.getRequiredAbilitiesVersion(), requirements, complete, warnings);
    }
    private JobDirection direction(JobDirection explicit, String text, List<String> warnings) {
        JobDirection mapped = dictionary.direction(text);
        if (explicit != null && mapped != null && mapped != explicit) {
            warnings.add("明确方向与文本类别矛盾，需要修正"); return null;
        }
        return explicit != null ? explicit : mapped;
    }
}
