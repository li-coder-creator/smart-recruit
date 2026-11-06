package com.recruit.smartrecruit.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.smartrecruit.dto.*;
import com.recruit.smartrecruit.entity.*;
import com.recruit.smartrecruit.entity.enums.*;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.JobRecommendationMapper;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.service.impl.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.recruit.smartrecruit.service.impl.RecommendationSnapshotReader.*;

class RecommendationQueryServiceTest {
    final RuleDictionary dict=new RuleDictionary();
    final RecommendationFeatureBuilder builder=new RecommendationFeatureBuilder(dict,new FeatureNormalizer(),new AbilityFeatureExtractor(dict));
    final RecommendationCalculator calculator=new RecommendationCalculator(new DimensionCalculator(),new SkillMatcher(dict,new DisabledSemanticAbilityMatcher()),new WeightedScoreCalculator(),dict);
    final Clock clock=Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"),ZoneId.of("Asia/Shanghai"));
    final PermissionService permission=mock(PermissionService.class);
    JobRecommendationQueryDTO request(String extra) throws Exception {return new ObjectMapper().readValue("{\"resumeId\":901"+extra+"}",JobRecommendationQueryDTO.class);}
    @Test void readerChecksRoleAndOwnershipBeforeReadingAnyChildData() {
        var mapper=mock(JobRecommendationMapper.class);var reader=new RecommendationSnapshotReader(mapper,permission);
        assertThrows(BusinessException.class,()->reader.context(101L,901L));verify(permission).requireJobSeeker(101L);verify(mapper).findOwnedResume(901L,101L);verifyNoMoreInteractions(mapper);
    }
    @Test void roleDenialPreventsEvenResumeLookup() {
        var mapper=mock(JobRecommendationMapper.class);doThrow(new BusinessException("仅求职者")).when(permission).requireJobSeeker(101L);
        assertThrows(BusinessException.class,()->new RecommendationSnapshotReader(mapper,permission).context(101L,901L));verifyNoInteractions(mapper);
    }
    @Test void abilitiesAreBatchedInsteadOfOneQueryPerJobAndEmptySourceIsExplicit() {
        var mapper=mock(JobRecommendationMapper.class);Resumebasic resume=new Resumebasic();resume.setId(901L);
        when(mapper.findOwnedResume(901L,101L)).thenReturn(resume);
        List<RecommendationJobRow> rows=new ArrayList<>();for(long id=1;id<=201;id++){var job=new RecommendationJobRow();job.setId(id);job.setRequiredAbilitiesSource(RequiredAbilitiesSource.STRUCTURED);rows.add(job);}
        when(mapper.jobs(eq(101L),any(),any(),any(),eq(true),eq(true))).thenReturn(rows);
        var snapshot=new RecommendationSnapshotReader(mapper,permission).load(101L,901L,LocalDate.now(clock),true,true);
        verify(mapper,times(2)).abilities(anyList());assertEquals(201,snapshot.abilities().size());assertTrue(snapshot.abilities().get(1L).isEmpty());
    }
    @Test void snapshotTransactionsAndApplicationBoundaryAreExplicit() throws Exception {
        var source=new AnnotationTransactionAttributeSource();
        var reader=source.getTransactionAttribute(RecommendationSnapshotReader.class.getMethod("load",Long.class,Long.class,LocalDate.class,boolean.class,boolean.class),RecommendationSnapshotReader.class);
        assertTrue(reader.isReadOnly());assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ,reader.getIsolationLevel());
        var service=source.getTransactionAttribute(JobRecommendationServiceImpl.class.getMethod("query",Long.class,JobRecommendationQueryDTO.class),JobRecommendationServiceImpl.class);
        assertEquals(TransactionDefinition.PROPAGATION_NOT_SUPPORTED,service.getPropagationBehavior());
    }
    ResumeInput input(JobPreference preference) {
        Resumebasic resume=new Resumebasic();resume.setId(901L);resume.setTitle("测试简历");resume.setFormalExperienceStatus(FormalExperienceStatus.NONE);
        ResumeSkill skill=new ResumeSkill();skill.setSkillName("Java");return new ResumeInput(resume,preference,List.of(),List.of(),List.of(skill));
    }
    JobRecommendationServiceImpl service(ResumeInput input) {
        var reader=mock(RecommendationSnapshotReader.class);when(reader.load(eq(101L),eq(901L),any(),anyBoolean(),anyBoolean())).thenReturn(new Snapshot(input,List.of(),Map.of()));
        return new JobRecommendationServiceImpl(reader,builder,calculator,dict,permission,clock);
    }
    @Test void explicitDirectionOverrideWinsWithoutChangingStoredPreference() throws Exception {
        JobPreference stored=new JobPreference();stored.setExpectedPosition("后端开发");stored.setDirectionCode(JobDirection.BACKEND);stored.setExpectedCity("上海");
        var result=service(input(stored)).query(101L,request(",\"preferences\":{\"directionCode\":\"FRONTEND\"}"));
        assertEquals(JobDirection.FRONTEND,result.appliedPreferences().directionCode());assertEquals(JobDirection.BACKEND,stored.getDirectionCode());assertEquals("后端开发",stored.getExpectedPosition());
    }
    @Test void explicitNullDirectionDoesNotFallBackToOldText() throws Exception {
        JobPreference stored=new JobPreference();stored.setExpectedPosition("后端开发");
        assertThrows(BusinessException.class,()->service(input(stored)).query(101L,request(",\"preferences\":{\"directionCode\":null}")));
    }
    @Test void missingDirectionAndStrictUnknownCityAreBusinessErrors() throws Exception {
        assertThrows(BusinessException.class,()->service(input(null)).query(101L,request("")));
        assertThrows(BusinessException.class,()->service(input(null)).query(101L,request(",\"strictCity\":true,\"preferences\":{\"directionCode\":\"BACKEND\"}")));
    }
    @Test void invalidLegacySalaryCanRemainUnknownButInvalidOverrideIsRejected() throws Exception {
        JobPreference stored=new JobPreference();stored.setDirectionCode(JobDirection.BACKEND);stored.setSalaryMin(30000);stored.setSalaryMax(20000);
        assertDoesNotThrow(()->service(input(stored)).query(101L,request("")));
        assertThrows(BusinessException.class,()->service(input(stored)).query(101L,request(",\"preferences\":{\"salaryMax\":20000}")));
    }
    @Test void maximumPageDoesNotOverflowAndDateIsFromInjectedClock() throws Exception {
        JobPreference stored=new JobPreference();stored.setDirectionCode(JobDirection.BACKEND);
        var result=service(input(stored)).query(101L,request(",\"page\":2147483647,\"size\":100"));
        assertTrue(result.items().isEmpty());assertEquals(LocalDate.of(2026,11,1),result.asOfDate());
    }
}
