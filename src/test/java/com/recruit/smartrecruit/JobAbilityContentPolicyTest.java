package com.recruit.smartrecruit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.smartrecruit.dto.JobContentSaveDTO;
import com.recruit.smartrecruit.entity.Job;
import com.recruit.smartrecruit.entity.enums.*;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.service.impl.JobAbilityContentPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JobAbilityContentPolicyTest {
    final ObjectMapper json = new ObjectMapper();
    final JobAbilityContentPolicy policy = new JobAbilityContentPolicy();
    Job legacy() {
        Job job = new Job(); job.setId(1L); job.setRequiredSkills("旧原文");
        job.setRequiredAbilitiesSource(RequiredAbilitiesSource.LEGACY_TEXT);
        job.setRequiredAbilitiesVersion(0L); return job;
    }
    JobContentSaveDTO dto(String text) throws Exception {return json.readValue(text, JobContentSaveDTO.class);}

    @Test void omittedTextPreservesLegacy() throws Exception {
        Job job = legacy(); policy.apply(dto("{\"title\":\"x\"}"),job);
        assertEquals("旧原文",job.getRequiredSkills());assertEquals(0L,job.getRequiredAbilitiesVersion());
    }
    @Test void explicitNullClearsLegacyAndIncrements() throws Exception {
        Job job=legacy();policy.apply(dto("{\"requiredSkills\":null}"),job);
        assertNull(job.getRequiredSkills());assertEquals(1L,job.getRequiredAbilitiesVersion());
    }
    @Test void replacementRetainsOriginalText() throws Exception {
        Job job=legacy();policy.apply(dto("{\"requiredAbilitiesVersion\":0,\"requiredAbilities\":[{\"rawText\":\" 熟悉Java \"},{\"rawText\":\"跨部门沟通\"}]}"),job);
        assertEquals(" 熟悉Java \n跨部门沟通",job.getRequiredSkills());assertEquals(1L,job.getRequiredAbilitiesVersion());
        assertEquals(RequiredAbilitiesSource.STRUCTURED,job.getRequiredAbilitiesSource());
    }
    @Test void staleVersionDoesNotMutateEntity() throws Exception {
        Job job=legacy();assertThrows(BusinessException.class,()->policy.apply(dto("{\"title\":\"bad\",\"requiredAbilitiesVersion\":1,\"requiredAbilities\":[]}"),job));
        assertNull(job.getTitle());assertEquals("旧原文",job.getRequiredSkills());
    }
    @Test void structuredNullArrayIsNotClear() throws Exception {
        Job job=legacy();job.setRequiredAbilitiesSource(RequiredAbilitiesSource.STRUCTURED);
        policy.apply(dto("{\"requiredAbilities\":null}"),job);
        assertEquals("旧原文",job.getRequiredSkills());assertEquals(0L,job.getRequiredAbilitiesVersion());
    }
    @Test void oldTextCannotChangeStructuredSource() throws Exception {
        Job job=legacy();job.setRequiredAbilitiesSource(RequiredAbilitiesSource.STRUCTURED);
        assertThrows(BusinessException.class,()->policy.apply(dto("{\"requiredSkills\":null}"),job));
    }
    @Test void emptyListKeepsStructuredSource() throws Exception {
        Job job=legacy();policy.apply(dto("{\"requiredAbilitiesVersion\":0,\"requiredAbilities\":[]}"),job);
        assertNull(job.getRequiredSkills());assertEquals(RequiredAbilitiesSource.STRUCTURED,job.getRequiredAbilitiesSource());
    }
    @Test void categoryChangeClearsOldDirection() throws Exception {
        Job job=legacy();job.setCategory("后端开发");job.setDirectionCode(JobDirection.BACKEND);
        policy.apply(dto("{\"category\":\"研发\"}"),job);assertNull(job.getDirectionCode());
    }
    @Test void directionConflictRejected() throws Exception {
        assertThrows(BusinessException.class,()->policy.apply(dto("{\"category\":\"后端开发\",\"directionCode\":\"FRONTEND\"}"),legacy()));
    }
    @Test void versionOverflowRejected() throws Exception {
        Job job=legacy();job.setRequiredAbilitiesVersion(Long.MAX_VALUE);
        assertThrows(BusinessException.class,()->policy.apply(dto("{\"requiredSkills\":\"新原文\"}"),job));
    }
}
