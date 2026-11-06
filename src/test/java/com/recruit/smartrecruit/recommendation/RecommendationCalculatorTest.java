package com.recruit.smartrecruit.recommendation;

import com.recruit.smartrecruit.entity.*;
import com.recruit.smartrecruit.entity.enums.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.recruit.smartrecruit.recommendation.RecommendationFeatures.*;
import static com.recruit.smartrecruit.recommendation.RecommendationTypes.*;
import static com.recruit.smartrecruit.recommendation.RecommendationWeights.Preset;

class RecommendationCalculatorTest {
    static final LocalDate AS_OF=LocalDate.of(2026,11,1);
    final RuleDictionary dictionary=new RuleDictionary();
    final FeatureNormalizer normalizer=new FeatureNormalizer();
    final AbilityFeatureExtractor extractor=new AbilityFeatureExtractor(dictionary);
    final RecommendationFeatureBuilder builder=new RecommendationFeatureBuilder(dictionary,normalizer,extractor);
    final DimensionCalculator dimensions=new DimensionCalculator();
    final WeightedScoreCalculator weighted=new WeightedScoreCalculator();
    final RecommendationCalculator calculator=new RecommendationCalculator(dimensions,new SkillMatcher(dictionary,new DisabledSemanticAbilityMatcher()),weighted,dictionary);
    static void decimal(String expected,BigDecimal actual) {assertNotNull(actual);assertEquals(0,new BigDecimal(expected).compareTo(actual),actual.toString());}
    static DimensionAssessment scored(String value) {return DimensionAssessment.scored(new BigDecimal(value),Reason.CALCULATED,null,List.of());}
    static EnumMap<Dimension,DimensionAssessment> scores(String skill,String salary,String city,String experience,String education) {
        var result=new EnumMap<Dimension,DimensionAssessment>(Dimension.class);
        result.put(Dimension.SKILL,scored(skill));result.put(Dimension.SALARY,scored(salary));result.put(Dimension.CITY,scored(city));
        result.put(Dimension.EXPERIENCE,scored(experience));result.put(Dimension.EDUCATION,scored(education));return result;
    }
    static RecommendationWeights only(Dimension dimension) {
        EnumMap<Dimension,Integer> values=new EnumMap<>(Dimension.class);
        for(var d:Dimension.values())values.put(d,d==dimension?100:0);return new RecommendationWeights(values);
    }
    Candidate candidate() {
        Resumebasic resume=new Resumebasic();resume.setId(1L);resume.setCareerStage(CareerStage.NEW_GRAD);resume.setFormalExperienceStatus(FormalExperienceStatus.NONE);
        JobPreference preference=new JobPreference();preference.setExpectedCity("Shanghai");preference.setSalaryMax(20000);
        List<ResumeSkill> skills=new ArrayList<>();for(String name:List.of("Java","SpringBoot","MySQL")){ResumeSkill skill=new ResumeSkill();skill.setSkillName(name);skills.add(skill);}
        return builder.candidate(resume,preference,List.of(FeatureNormalizerTest.education("Bachelor","2020-09-01","2024-06-30")),List.of(),skills,AS_OF);
    }
    Job job() {
        Job job=new Job();job.setId(1L);job.setCity("上海市");job.setSalaryMin(10000);job.setSalaryMax(20000);job.setSalaryUnit(JobSalaryUnit.MONTH);
        job.setNegotiable(false);job.setRequiredAbilitiesSource(RequiredAbilitiesSource.LEGACY_TEXT);job.setRequiredAbilitiesVersion(0L);
        job.setRequiredSkills("Java,Spring Boot,MySQL,Redis");job.setExperience("1-3");job.setEducation("硕士");job.setPublishedAt(LocalDateTime.of(2026,11,1,10,0));return job;
    }
    @Test void w1AllDimensionsFull() {
        var result=weighted.aggregate(scores("100","100","100","100","100"),Preset.BALANCED.weights());
        decimal("100.00",result.totalScore());decimal("100",result.dataCoverage());decimal("0",result.excludedWeight());
    }
    @Test void w2ExactHandCalculatedExample() {
        var result=weighted.aggregate(scores("75","75","100","55","0"),Preset.BALANCED.weights());
        decimal("70.50",result.totalScore());decimal("26.25",result.dimensions().get(Dimension.SKILL).contribution());
    }
    @Test void w3NotApplicableExcludesAndRenormalizesWeight() {
        var input=scores("75","75","100","55","0");input.put(Dimension.SALARY,DimensionAssessment.notApplicable(Reason.DIFFERENT_SALARY_UNIT,"日薪"));
        var result=weighted.aggregate(input,Preset.BALANCED.weights());decimal("69",result.totalScore());decimal("100",result.dataCoverage());decimal("25",result.excludedWeight());
    }
    @Test void w4UnknownRetainsDenominatorAndContributesZero() {
        var input=scores("75","75","100","55","0");input.put(Dimension.SALARY,DimensionAssessment.unknown(Reason.DATA_MISSING,"薪资缺失"));
        var result=weighted.aggregate(input,Preset.BALANCED.weights());decimal("51.75",result.totalScore());decimal("75",result.dataCoverage());decimal("0",result.excludedWeight());
        decimal("0.25",result.dimensions().get(Dimension.SALARY).effectiveWeightRatio());assertNull(result.dimensions().get(Dimension.SALARY).assessment().score());
    }
    @Test void w5UnknownCoreSkillsNullifiesTotalButRetainsOtherScores() {
        var input=scores("75","75","100","55","0");input.put(Dimension.SKILL,DimensionAssessment.unknown(Reason.DATA_MISSING,"技能未填"));
        var result=weighted.aggregate(input,Preset.BALANCED.weights());assertNull(result.totalScore());assertEquals(Reason.CORE_SKILLS_UNKNOWN,result.reason());
        decimal("65",result.dataCoverage());decimal("75",result.dimensions().get(Dimension.SALARY).assessment().score());
    }
    @Test void w6AllPositiveWeightNotApplicableAvoidsDivisionByZero() {
        var input=scores("0","0","0","0","0");input.put(Dimension.SALARY,DimensionAssessment.notApplicable(Reason.DIFFERENT_SALARY_UNIT,"日薪"));
        var result=weighted.aggregate(input,only(Dimension.SALARY));assertNull(result.totalScore());assertEquals(Reason.NO_COMPARABLE_DIMENSIONS,result.reason());
        decimal("0",result.dataCoverage());decimal("100",result.excludedWeight());
    }
    @Test void w7ZeroMissingNegativeAndExcessiveWeightsAreRejected() {
        EnumMap<Dimension,Integer> values=new EnumMap<>(Dimension.class);for(var d:Dimension.values())values.put(d,0);
        assertThrows(IllegalArgumentException.class,()->new RecommendationWeights(values));values.put(Dimension.SKILL,-1);
        assertThrows(IllegalArgumentException.class,()->new RecommendationWeights(values));values.put(Dimension.SKILL,101);
        assertThrows(IllegalArgumentException.class,()->new RecommendationWeights(values));values.remove(Dimension.SKILL);
        assertThrows(IllegalArgumentException.class,()->new RecommendationWeights(values));
    }
    @Test void explicitZeroIsDifferentFromNoScoredDimensions() {
        var result=weighted.aggregate(scores("0","0","0","0","0"),Preset.BALANCED.weights());decimal("0",result.totalScore());decimal("100",result.dataCoverage());
        var input=scores("0","0","0","0","0");input.put(Dimension.SALARY,DimensionAssessment.unknown(Reason.DATA_MISSING,"缺失"));
        var unknown=weighted.aggregate(input,only(Dimension.SALARY));assertNull(unknown.totalScore());assertEquals(Reason.NO_SCORED_DIMENSIONS,unknown.reason());
    }
    @Test void zeroWeightSkillsAreIgnoredWithoutFakingSkillScore() {
        var input=scores("0","75","100","100","100");input.put(Dimension.SKILL,DimensionAssessment.unknown(Reason.DATA_MISSING,"缺失"));
        var result=weighted.aggregate(input,only(Dimension.SALARY));decimal("75",result.totalScore());decimal("100",result.dataCoverage());
        assertTrue(result.dimensions().get(Dimension.SKILL).ignored());assertNull(result.dimensions().get(Dimension.SKILL).assessment().score());
    }
    @Test void relativeCustomWeightsNormalizeAndResultsAreImmutable() {
        EnumMap<Dimension,Integer> map=new EnumMap<>(Dimension.class);for(var d:Dimension.values())map.put(d,1);
        var weights=new RecommendationWeights(map);map.put(Dimension.SKILL,100);
        var result=weighted.aggregate(scores("75","75","100","55","0"),weights);decimal("61",result.totalScore());
        assertEquals(1,weights.get(Dimension.SKILL));assertThrows(UnsupportedOperationException.class,()->weights.values().clear());
        assertThrows(UnsupportedOperationException.class,()->result.dimensions().clear());
    }
    @Test void totalDoesNotSumRoundedDisplayContributions() {
        EnumMap<Dimension,Integer> weights=new EnumMap<>(Dimension.class);for(var d:Dimension.values())weights.put(d,0);
        weights.put(Dimension.SKILL,1);weights.put(Dimension.SALARY,1);weights.put(Dimension.CITY,1);
        var result=weighted.aggregate(scores("100","100","100","0","0"),new RecommendationWeights(weights));decimal("100",result.totalScore());
        decimal("0.333333",result.dimensions().get(Dimension.SKILL).effectiveWeightRatio());
        decimal("99.9999",result.dimensions().values().stream().map(WeightedScoreCalculator.WeightedDimension::contribution).reduce(BigDecimal.ZERO,BigDecimal::add));
    }
    @Test void p1SalaryMidpointAndIntegerOverflowBoundary() {
        decimal("75",dimensions.salary(candidate().preference(),new SalaryOffer(10000,20000,JobSalaryUnit.MONTH,false)).score());
        decimal("100",dimensions.salary(new Preference(null,null,null,null,null,Integer.MAX_VALUE),new SalaryOffer(Integer.MAX_VALUE,Integer.MAX_VALUE,JobSalaryUnit.MONTH,false)).score());
    }
    @Test void salaryMissingOrConflictingDataIsNotNotApplicable() {
        for(var offer:List.of(new SalaryOffer(100,200,JobSalaryUnit.MONTH,true),new SalaryOffer(200,100,JobSalaryUnit.DAY,false),
                new SalaryOffer(null,null,JobSalaryUnit.DAY,false),new SalaryOffer(-1,100,JobSalaryUnit.MONTH,false),
                new SalaryOffer(100,200,null,false),new SalaryOffer(100,200,JobSalaryUnit.MONTH,null)))
            assertEquals(EvaluationState.UNKNOWN,dimensions.salary(candidate().preference(),offer).state());
    }
    @Test void legitimateNegotiableDayAndYearSalaryAreNotApplicable() {
        for(var offer:List.of(new SalaryOffer(null,null,JobSalaryUnit.MONTH,true),new SalaryOffer(100,200,JobSalaryUnit.DAY,false),new SalaryOffer(10000,20000,JobSalaryUnit.YEAR,false)))
            assertEquals(EvaluationState.NOT_APPLICABLE,dimensions.salary(candidate().preference(),offer).state());
    }
    @Test void expectedSalaryValidationAndLowUpperBoundWarning() {
        var offer=new SalaryOffer(10000,20000,JobSalaryUnit.MONTH,false);
        for(var preference:List.of(new Preference(null,null,null,null,null,null),new Preference(null,null,null,null,0,20000),
                new Preference(null,null,null,null,30000,20000),new Preference(null,null,null,null,null,0)))
            assertEquals(EvaluationState.UNKNOWN,dimensions.salary(preference,offer).state());
        var result=dimensions.salary(new Preference(null,null,null,null,25000,30000),offer);assertFalse(result.warnings().isEmpty());
    }
    @Test void cityKnownMismatchScoresZeroUnknownCityStaysUnknown() {
        Job job=job();job.setCity("深圳");decimal("0",dimensions.city(candidate().preference(),builder.job(job,List.of(),AS_OF)).score());
        job.setCity("华东");assertNull(dimensions.city(candidate().preference(),builder.job(job,List.of(),AS_OF)).score());
    }
    @Test void e2ExperienceGapUsesMergedEighteenMonths() {
        Resumebasic resume=new Resumebasic();resume.setFormalExperienceStatus(FormalExperienceStatus.HAS_EXPERIENCE);
        var c=builder.candidate(resume,null,List.of(),List.of(FeatureNormalizerTest.work("2024-01-01","2024-12-31",ExperienceType.FORMAL,false),
                FeatureNormalizerTest.work("2024-07-01","2025-06-30",ExperienceType.FORMAL,false)),List.of(),AS_OF);
        decimal("32.50",dimensions.experience(c,normalizer.experienceRequirement("3–5年")).score());
    }
    @Test void e3ZeroConfirmedFormalExperienceAndClampedPenalty() {
        decimal("55",dimensions.experience(candidate(),normalizer.experienceRequirement("1-3")).score());
        decimal("0",dimensions.experience(candidate(),normalizer.experienceRequirement("5年以上")).score());
    }
    @Test void explicitUnlimitedNeedsNoExperienceProofAndStagesUseDeclaration() {
        Resumebasic resume=new Resumebasic();var c=builder.candidate(resume,null,List.of(),List.of(),List.of(),AS_OF);
        decimal("100",dimensions.experience(c,normalizer.experienceRequirement("不限")).score());
        assertNull(dimensions.experience(c,normalizer.experienceRequirement("应届生")).score());
        decimal("100",dimensions.experience(candidate(),normalizer.experienceRequirement("应届生")).score());
        decimal("0",dimensions.experience(candidate(),normalizer.experienceRequirement("在校生")).score());
    }
    @Test void educationUsesBinaryScoresWithoutRemovingWeight() {
        decimal("100",dimensions.education(candidate().education(),normalizer.educationRequirement("本科")).score());
        var below=dimensions.education(candidate().education(),normalizer.educationRequirement("硕士"));decimal("0",below.score());assertFalse(below.meetsRequirement());
    }
    @Test void currentMastersCountsAsDeclaredBackgroundNotCertificate() {
        var background=normalizer.education(List.of(FeatureNormalizerTest.education("硕士研究生","2026-09-01","2029-06-30")),AS_OF);
        decimal("100",dimensions.education(background,normalizer.educationRequirement("硕士")).score());assertEquals(StudyStage.IN_PROGRESS,background.evidence().get(0).stage());
    }
    @Test void unknownHighEducationCanChangeFailureButCannotUndoKnownSuccess() {
        var b=normalizer.education(List.of(FeatureNormalizerTest.education("本科","2020-09-01","2024-06-30"),FeatureNormalizerTest.education("硕士",null,null)),AS_OF);
        decimal("100",dimensions.education(b,normalizer.educationRequirement("本科")).score());
        assertNull(dimensions.education(b,normalizer.educationRequirement("硕士")).score());
        decimal("0",dimensions.education(b,normalizer.educationRequirement("博士")).score());
    }
    @Test void emptyEducationIsUnknownUnlessRequirementExplicitlyAny() {
        var b=normalizer.education(List.of(),AS_OF);assertNull(dimensions.education(b,normalizer.educationRequirement("本科")).score());
        decimal("100",dimensions.education(b,normalizer.educationRequirement("学历不限")).score());
    }
    @Test void endToEndW2AndRuleVersions() {
        var result=calculator.calculate(candidate(),builder.job(job(),List.of(),AS_OF),Preset.BALANCED.weights());decimal("70.50",result.score().totalScore());
        assertEquals(AS_OF,result.asOfDate());assertEquals("five-dimensions-v1",result.versions().scoringRuleVersion());
        assertEquals("skill-alias-v1",result.versions().aliasVersion());assertNull(result.versions().embeddingModelVersion());assertNull(result.versions().calibrationVersion());
    }
    @Test void e2eUnknownCoreAndSameDateGuard() {
        Job job=job();job.setRequiredSkills("跨部门沟通");
        var result=calculator.calculate(candidate(),builder.job(job,List.of(),AS_OF),Preset.BALANCED.weights());assertNull(result.score().totalScore());assertEquals(Reason.CORE_SKILLS_UNKNOWN,result.score().reason());
        assertThrows(IllegalArgumentException.class,()->calculator.calculate(candidate(),builder.job(job,List.of(),AS_OF.plusDays(1)),Preset.BALANCED.weights()));
    }
    @Test void sortingUsesUnroundedScoreThenPublishedTimeAndIdWithNullLast() {
        var low=weighted.aggregate(scores("0","70.501","0","0","0"),only(Dimension.SALARY));
        var high=weighted.aggregate(scores("0","70.504","0","0","0"),only(Dimension.SALARY));
        decimal("70.50",low.totalScore());decimal("70.50",high.totalScore());
        var input=scores("0","0","0","0","0");input.put(Dimension.SALARY,DimensionAssessment.unknown(Reason.DATA_MISSING,"missing"));
        var unknown=weighted.aggregate(input,only(Dimension.SALARY));var time=LocalDateTime.of(2026,11,1,10,0);
        List<RecommendationScore> list=new ArrayList<>(List.of(new RecommendationScore(9L,time.plusDays(1),AS_OF,low,null,null),
                new RecommendationScore(8L,time,AS_OF,high,null,null),new RecommendationScore(10L,time,AS_OF,high,null,null),
                new RecommendationScore(11L,null,AS_OF,high,null,null),new RecommendationScore(99L,time.plusDays(9),AS_OF,unknown,null,null)));
        list.sort(RecommendationScore.ranking());assertEquals(List.of(10L,8L,11L,9L,99L),list.stream().map(RecommendationScore::jobId).toList());
    }
    @Test void statusesCannotCarryFabricatedScoresOrMeetsFlags() {
        assertThrows(IllegalArgumentException.class,()->new DimensionAssessment(EvaluationState.UNKNOWN,BigDecimal.ZERO,Reason.DATA_MISSING,null,List.of()));
        assertThrows(IllegalArgumentException.class,()->new DimensionAssessment(EvaluationState.UNKNOWN,null,Reason.DATA_MISSING,true,List.of()));
        assertThrows(IllegalArgumentException.class,()->scored("101"));
    }
    @Test void springComponentsWireWithoutDatabaseRedisOrModelConnection() {
        try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.scan("com.recruit.smartrecruit.recommendation");context.refresh();
            var service=context.getBean(RecommendationCalculator.class);
            decimal("70.50",service.calculate(candidate(),builder.job(job(),List.of(),AS_OF),Preset.BALANCED.weights()).score().totalScore());
            assertFalse(context.containsBean("dataSource"));assertFalse(context.containsBean("redisTemplate"));
        }
    }
}
