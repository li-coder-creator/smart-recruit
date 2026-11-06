package com.recruit.smartrecruit.recommendation;

import com.recruit.smartrecruit.entity.enums.*;
import java.time.*;
import java.util.*;
import static com.recruit.smartrecruit.recommendation.RecommendationTypes.*;

/** 不可变的派生快照，仅包含评分所需信息，不携带姓名、联系方式、学校名气等。 */
public final class RecommendationFeatures {
    private RecommendationFeatures() {}
    public record DateInterval(LocalDate start, LocalDate endExclusive) {}
    public record FormalExperience(Long actualMonths, long confirmedMonths, boolean hasUnknown,
                                   List<DateInterval> mergedIntervals, List<String> warnings) {
        public FormalExperience { mergedIntervals = List.copyOf(mergedIntervals); warnings = List.copyOf(warnings); }
    }
    public record EducationEvidence(String rawDegree, EducationLevel level, StudyStage stage) {}
    public record EducationBackground(EducationLevel highest, int potentialUnknownRank,
                                      List<EducationEvidence> evidence, List<String> warnings) {
        public EducationBackground { evidence = List.copyOf(evidence); warnings = List.copyOf(warnings); }
    }
    public record ExperienceRequirement(String rawText, RequirementKind kind, Long minimumMonths, Long upperMonths) {}
    public record EducationRequirement(String rawText, RequirementKind kind, EducationLevel minimum) {}
    public record SalaryOffer(Integer minimum, Integer maximum, JobSalaryUnit unit, Boolean negotiable) {}
    public record Preference(String rawPosition, JobDirection direction, String rawCity, String cityCode,
                             Integer salaryMinimum, Integer salaryMaximum) {}
    public record AbilityFeature(String sourceRef, String rawText, String description, String normalizedName,
                                 String canonicalName, AbilityProficiency proficiency, Polarity polarity,
                                 boolean constrained, boolean levelRequirement, List<String> warnings) {
        public AbilityFeature { warnings = List.copyOf(warnings); }
    }
    public record Candidate(Long resumeId, LocalDate asOfDate, CareerStage careerStage, Preference preference,
                            FormalExperience experience, EducationBackground education,
                            List<AbilityFeature> abilities, List<String> warnings) {
        public Candidate { abilities = List.copyOf(abilities); warnings = List.copyOf(warnings); }
    }
    public record JobProfile(Long jobId, LocalDate asOfDate, LocalDateTime publishedAt, JobDirection direction,
                             String rawCity, String cityCode, SalaryOffer salary,
                             ExperienceRequirement experience, EducationRequirement education,
                             RequiredAbilitiesSource abilitiesSource, Long abilitiesVersion,
                             List<AbilityFeature> abilities, boolean requirementsComplete, List<String> warnings) {
        public JobProfile { abilities = List.copyOf(abilities); warnings = List.copyOf(warnings); }
    }
}
