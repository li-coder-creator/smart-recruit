package com.recruit.smartrecruit.recommendation;

import com.recruit.smartrecruit.entity.*;
import com.recruit.smartrecruit.entity.enums.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.recruit.smartrecruit.recommendation.RecommendationTypes.*;

class FeatureNormalizerTest {
    static final LocalDate AS_OF = LocalDate.of(2026, 11, 1);
    final FeatureNormalizer normalizer = new FeatureNormalizer();
    final RuleDictionary dictionary = new RuleDictionary();
    final RecommendationFeatureBuilder builder = new RecommendationFeatureBuilder(dictionary, normalizer, new AbilityFeatureExtractor(dictionary));
    static ResumeExperience work(String start, String end, ExperienceType type, Boolean current) {
        ResumeExperience result = new ResumeExperience(); result.setExperienceType(type); result.setIsCurrent(current);
        result.setStartDate(start == null ? null : LocalDate.parse(start)); result.setEndDate(end == null ? null : LocalDate.parse(end)); return result;
    }
    static ResumeEducation education(String degree, String start, String end) {
        ResumeEducation result = new ResumeEducation(); result.setDegree(degree);
        result.setStartDate(start == null ? null : LocalDate.parse(start)); result.setEndDate(end == null ? null : LocalDate.parse(end)); return result;
    }
    @ParameterizedTest @CsvSource({"1-3,12,36", "１–３年,12,36", "3—5年,36,60", "5年以上,60,", "3年,36,"})
    void parsesOnlyExplicitMinimumYears(String raw, long minimum, Long upper) {
        var result = normalizer.experienceRequirement(raw);
        assertEquals(RequirementKind.MINIMUM, result.kind());assertEquals(minimum, result.minimumMonths());assertEquals(upper, result.upperMonths());
    }
    @ParameterizedTest @ValueSource(strings={"", "相关经验优先", "1年以内", "3-1年", "0-3年", "Java3年熟悉", "3", "999999999999999999999年", "1-3年以上"})
    void ambiguousOrOverflowingExperienceStaysUnknown(String raw) { assertEquals(RequirementKind.UNKNOWN,normalizer.experienceRequirement(raw).kind()); }
    @ParameterizedTest @ValueSource(strings={"不限", "经验不限", "0年", "无需经验", "无经验要求"})
    void explicitUnlimitedExperience(String raw) { assertEquals(RequirementKind.ANY,normalizer.experienceRequirement(raw).kind()); }
    @Test void stageIsNotInferredFromYears() {
        assertEquals(RequirementKind.STUDENT,normalizer.experienceRequirement("在校生").kind());
        assertEquals(RequirementKind.NEW_GRAD,normalizer.experienceRequirement("应届毕业生").kind());
        assertEquals(RequirementKind.UNKNOWN,normalizer.experienceRequirement(null).kind());
    }
    @Test void overlappingPeriodsMergeToEighteenMonths() {
        var result = normalizer.formalExperience(FormalExperienceStatus.HAS_EXPERIENCE,List.of(
                work("2024-01-01","2024-12-31",ExperienceType.FORMAL,false),work("2024-07-01","2025-06-30",ExperienceType.FORMAL,false)),AS_OF);
        assertEquals(18L,result.actualMonths());assertEquals(18,result.confirmedMonths());assertEquals(1,result.mergedIntervals().size());
        assertEquals(LocalDate.of(2025,7,1),result.mergedIntervals().get(0).endExclusive());
    }
    @Test void adjacentIntervalsMergeBeforeCountingMonths() {
        var result=normalizer.formalExperience(FormalExperienceStatus.HAS_EXPERIENCE,List.of(
                work("2024-01-01","2024-01-15",ExperienceType.FORMAL,false),work("2024-01-16","2024-01-31",ExperienceType.FORMAL,false)),AS_OF);
        assertEquals(1L,result.actualMonths());
    }
    @Test void disconnectedRemainderDaysDoNotCombine() {
        var result=normalizer.formalExperience(FormalExperienceStatus.HAS_EXPERIENCE,List.of(
                work("2024-01-01","2024-01-15",ExperienceType.FORMAL,false),work("2024-02-01","2024-02-15",ExperienceType.FORMAL,false)),AS_OF);
        assertEquals(0L,result.actualMonths());assertEquals(2,result.mergedIntervals().size());
    }
    @Test void currentUsesFixedDateAndInclusiveEnd() {
        var result=normalizer.formalExperience(FormalExperienceStatus.HAS_EXPERIENCE,
                List.of(work("2025-11-01",null,ExperienceType.FORMAL,true)),AS_OF);
        assertEquals(12L,result.actualMonths());
    }
    @Test void internshipDoesNotCountAndNoneIsRequiredToProveZero() {
        var records=List.of(work("2024-01-01","2024-12-31",ExperienceType.INTERNSHIP,false));
        assertEquals(0L,normalizer.formalExperience(FormalExperienceStatus.NONE,records,AS_OF).actualMonths());
        assertNull(normalizer.formalExperience(FormalExperienceStatus.UNKNOWN,records,AS_OF).actualMonths());
        assertNull(normalizer.formalExperience(FormalExperienceStatus.HAS_EXPERIENCE,List.of(),AS_OF).actualMonths());
        assertNull(normalizer.formalExperience(FormalExperienceStatus.UNKNOWN,List.of(),AS_OF).actualMonths());
    }
    @Test void noneConflictDoesNotBecomeKnownZero() {
        assertNull(normalizer.formalExperience(FormalExperienceStatus.NONE,List.of(work("2024-01-01","2024-12-31",ExperienceType.FORMAL,false)),AS_OF).actualMonths());
        assertNull(normalizer.formalExperience(FormalExperienceStatus.NONE,List.of(work(null,null,ExperienceType.UNKNOWN,null)),AS_OF).actualMonths());
    }
    @Test void partialKnownMonthsAreNotCompleteHistory() {
        var result=normalizer.formalExperience(FormalExperienceStatus.HAS_EXPERIENCE,List.of(
                work("2024-01-01","2024-12-31",ExperienceType.FORMAL,false),work(null,null,ExperienceType.UNKNOWN,null)),AS_OF);
        assertEquals(12,result.confirmedMonths());assertNull(result.actualMonths());assertTrue(result.hasUnknown());
    }
    @Test void invalidFormalDatesRemainUnknown() {
        for(var record:List.of(work(null,null,ExperienceType.FORMAL,true),work("2027-01-01",null,ExperienceType.FORMAL,true),
                work("2025-01-01",null,ExperienceType.FORMAL,false),work("2025-01-01",null,ExperienceType.FORMAL,null),
                work("2025-01-01","2025-02-01",ExperienceType.FORMAL,true),work("2025-01-01","2027-01-01",ExperienceType.FORMAL,false),
                work("2025-03-01","2025-02-01",ExperienceType.FORMAL,false)))
            assertNull(normalizer.formalExperience(FormalExperienceStatus.HAS_EXPERIENCE,List.of(record),AS_OF).actualMonths());
    }
    @Test void periodTextIsNotAnInferredCurrentFlag() {
        var record=work("2025-01-01",null,ExperienceType.FORMAL,null);record.setPeriodText("至今");
        assertNull(normalizer.formalExperience(FormalExperienceStatus.HAS_EXPERIENCE,List.of(record),AS_OF).actualMonths());assertNull(record.getIsCurrent());
    }
    @Test void highestStartedEducationIncludesCurrentMasters() {
        var result=normalizer.education(List.of(education("Master","2026-09-01","2029-06-30"),education("Bachelor","2020-09-01","2024-06-30")),AS_OF);
        assertEquals(EducationLevel.MASTER,result.highest());assertEquals(StudyStage.IN_PROGRESS,result.evidence().get(0).stage());
    }
    @Test void plannedEducationDoesNotRaiseCurrentLevel() {
        var result=normalizer.education(List.of(education("本科","2020-09-01","2024-06-30"),education("硕士","2027-09-01","2030-06-30")),AS_OF);
        assertEquals(EducationLevel.BACHELOR,result.highest());assertEquals(0,result.potentialUnknownRank());assertEquals(StudyStage.PLANNED,result.evidence().get(1).stage());
    }
    @Test void missingEndDateIsStartedBackgroundNotVerifiedGraduation() {
        var result=normalizer.education(List.of(education("Ph.D.","2024-09-01",null)),AS_OF);
        assertEquals(EducationLevel.DOCTOR,result.highest());assertEquals(StudyStage.STAGE_UNKNOWN,result.evidence().get(0).stage());
    }
    @Test void uncertaintyRetainsPossibleEducationRank() {
        var result=normalizer.education(List.of(education("本科","2020-09-01","2024-06-30"),education("硕士",null,null)),AS_OF);
        assertEquals(EducationLevel.BACHELOR,result.highest());assertEquals(EducationLevel.MASTER.rank(),result.potentialUnknownRank());
        assertEquals(EducationLevel.DOCTOR.rank(),normalizer.education(List.of(education("未知学历","2020-01-01",null)),AS_OF).potentialUnknownRank());
    }
    @Test void graduationConstraintsDoNotDisappear() {
        assertEquals(EducationLevel.BACHELOR,normalizer.educationRequirement("本科及以上").minimum());
        assertEquals(EducationLevel.BACHELOR,normalizer.educationRequirement("Bachelor").minimum());
        assertEquals(RequirementKind.UNKNOWN,normalizer.educationRequirement("已取得本科").kind());
        assertEquals(RequirementKind.UNKNOWN,normalizer.educationRequirement("本科必须毕业").kind());
    }
    @Test void exactCityAndDirectionAliasesOnly() {
        assertEquals(dictionary.city("Shanghai"),dictionary.city("上海市"));assertNull(dictionary.city("华东"));assertNull(dictionary.city(null));
        assertEquals(JobDirection.BACKEND,dictionary.direction("后端开发"));assertNull(dictionary.direction("技术研发"));
    }
    @Test void structuredEmptyNeverFallsBackAndUnloadedIsProgrammingError() {
        Job job=new Job();job.setRequiredAbilitiesSource(RequiredAbilitiesSource.STRUCTURED);job.setRequiredSkills("Java");
        assertTrue(builder.job(job,List.of(),AS_OF).abilities().isEmpty());
        assertThrows(NullPointerException.class,()->builder.job(job,null,AS_OF));
        job.setRequiredAbilitiesSource(null);assertFalse(builder.job(job,List.of(),AS_OF).requirementsComplete());
    }
    @Test void childOrderAndRawTextArePreservedWithoutMutatingEntities() {
        Job job=new Job();job.setRequiredAbilitiesSource(RequiredAbilitiesSource.STRUCTURED);
        JobRequiredAbility a=new JobRequiredAbility();a.setId(2L);a.setSortOrder(1);a.setRawText("Redis");
        JobRequiredAbility b=new JobRequiredAbility();b.setId(1L);b.setSortOrder(0);b.setRawText(" 熟悉Java ");
        var result=builder.job(job,List.of(a,b),AS_OF);assertEquals(" 熟悉Java ",result.abilities().get(0).rawText());
        b.setRawText("后续修改");assertEquals(" 熟悉Java ",result.abilities().get(0).rawText());
        assertThrows(UnsupportedOperationException.class,()->result.abilities().clear());
    }
    @Test void preferencesUseSelectedResumeNotCurrentResidence() {
        Resumebasic resume=new Resumebasic();resume.setCurrentCity("上海");
        var candidate=builder.candidate(resume,null,List.of(),List.of(),List.of(),AS_OF);
        assertNull(candidate.preference().cityCode());assertNull(candidate.preference().salaryMaximum());
    }
    @Test void conflictingDirectionDoesNotSilentlyWin() {
        Job job=new Job();job.setCategory("后端开发");job.setDirectionCode(JobDirection.FRONTEND);
        assertNull(builder.job(job,List.of(),AS_OF).direction());
        job.setCategory("技术");assertEquals(JobDirection.FRONTEND,builder.job(job,List.of(),AS_OF).direction());
    }
    @Test void structuredChildrenAreAuthoritativeEvenIfLegacyProjectionDiffers() {
        Job job=new Job();job.setRequiredAbilitiesSource(RequiredAbilitiesSource.STRUCTURED);job.setRequiredSkills("Java");
        JobRequiredAbility ability=new JobRequiredAbility();ability.setRawText("Redis");ability.setSortOrder(0);
        var result=builder.job(job,List.of(ability),AS_OF);assertEquals("redis",result.abilities().get(0).canonicalName());
    }
}
