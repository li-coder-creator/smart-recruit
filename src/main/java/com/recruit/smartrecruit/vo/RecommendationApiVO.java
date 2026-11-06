package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.dto.JobRecommendationQueryDTO.*;
import com.recruit.smartrecruit.entity.enums.*;
import com.recruit.smartrecruit.recommendation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.recruit.smartrecruit.recommendation.RecommendationTypes.*;

/** 独立公开契约；不把Mapper实体、完整简历或内部未舍入排序值返回客户端。 */
public final class RecommendationApiVO {
    private RecommendationApiVO() {}
    public record Choice<T>(T code,String label) {}
    public record PresetOption(Preset code,String label,Map<Dimension,Integer> weights) {}
    public record Options(List<Choice<JobDirection>> directions,List<Choice<String>> cities,List<PresetOption> presets,
                          List<Choice<Sort>> sorts,List<Choice<EvaluationState>> states,Map<Dimension,Integer> defaultWeights,
                          boolean defaultStrictCity,boolean defaultExcludeApplied,boolean defaultExcludeExpired,
                          int defaultPage,int defaultSize,int maxPageSize,String salaryUnit,boolean semanticEnabled,
                          RecommendationScore.Versions versions) {}
    public record Preferences(JobDirection directionCode,String city,String cityCode,Integer salaryMin,Integer salaryMax) {}
    public record SkillSummary(String rawText,String description,AbilityProficiency proficiency) {}
    public record Context(Long resumeId,String resumeTitle,LocalDate asOfDate,CareerStage careerStage,
                          FormalExperienceStatus formalExperienceStatus,Long actualFormalMonths,long confirmedFormalMonths,
                          EducationLevel highestEducation,List<SkillSummary> skills,Preferences preferences,
                          boolean directionRequired,List<String> warnings) {}
    public record DimensionBreakdown(Dimension dimension,EvaluationState state,BigDecimal score,Boolean meetsRequirement,
                                     Reason reason,int requestedWeight,boolean ignored,BigDecimal effectiveWeightRatio,
                                     BigDecimal contribution,List<String> warnings) {}
    public record AbilityEvidence(String sourceRef,String rawText,String description,String normalizedName,String canonicalName,
                                  AbilityProficiency proficiency,Polarity polarity,List<String> warnings) {}
    public record AbilityMatch(List<AbilityEvidence> requirements,AbilityEvidence candidateEvidence,MatchMethod method,
                               MatchDecision decision,BigDecimal score,BigDecimal similarity,Reason reason) {}
    public record Item(Long jobId,String title,Long companyId,String companyName,String companyLogo,String city,
                       String experience,String education,String employment,String team,Integer salaryMin,Integer salaryMax,
                       JobSalaryUnit salaryUnit,Boolean negotiable,String summary,String benefits,String requiredSkills,
                       String preferredSkills,JobDirection directionCode,LocalDateTime publishedAt,LocalDate deadline,
                       boolean applied,boolean expired,RequiredAbilitiesSource requiredAbilitiesSource,Long requiredAbilitiesVersion,
                       BigDecimal totalScore,BigDecimal dataCoverage,BigDecimal excludedWeight,Reason scoreReason,
                       List<DimensionBreakdown> dimensions,List<AbilityMatch> abilityMatches,List<String> warnings) {}
    public record QueryResult(Long resumeId,LocalDate asOfDate,Preferences appliedPreferences,Map<Dimension,Integer> requestedWeights,
                              boolean strictCity,boolean excludeApplied,boolean excludeExpired,Sort sort,int page,int size,
                              long total,List<Item> items,boolean semanticEnabled,RecommendationScore.Versions versions) {}
}
