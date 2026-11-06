package com.recruit.smartrecruit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.smartrecruit.constant.RecommendationInputConstants;
import com.recruit.smartrecruit.dto.*;
import com.recruit.smartrecruit.entity.*;
import com.recruit.smartrecruit.entity.enums.*;
import com.recruit.smartrecruit.vo.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** 纯契约测试，不启动Spring、不调用HTTP、不读写业务数据库。接口回归仍使用Postman MCP。 */
class RecommendationInputContractTest {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeValidator() {
        FACTORY.close();
    }

    @Test
    void createDefaultsMeanUnknownAndExplicitNullIsRejected() throws Exception {
        var dto = JSON.readValue("{\"title\":\"我的简历\"}", ResumeProfileCreateDTO.class);
        assertEquals(CareerStage.UNKNOWN, dto.getCareerStage());
        assertEquals(FormalExperienceStatus.UNKNOWN, dto.getFormalExperienceStatus());
        assertTrue(VALIDATOR.validate(dto).isEmpty());
        assertThrows(JsonProcessingException.class, () -> JSON.readValue(
                "{\"title\":\"我的简历\",\"careerStage\":null}", ResumeProfileCreateDTO.class));
    }

    @Test
    void allEnumsUseStringCodesAndRejectOrdinalsAndUnknownCodes() throws Exception {
        for (Class<?> type : List.of(CareerStage.class, FormalExperienceStatus.class, ExperienceType.class,
                AbilityProficiency.class, JobDirection.class, RequiredAbilitiesSource.class)) {
            for (Object value : type.getEnumConstants()) {
                assertEquals(value, JSON.readValue(JSON.writeValueAsString(value), type));
            }
            assertThrows(JsonProcessingException.class, () -> JSON.readValue("0", type));
            assertThrows(JsonProcessingException.class, () -> JSON.readValue("\"NOT_A_CODE\"", type));
        }
    }

    @Test
    void updatingADeclarationDistinguishesOmissionFromUnknownReset() throws Exception {
        var old = JSON.readValue("{\"title\":\"我的简历\"}", ResumeProfileUpdateDTO.class);
        assertFalse(old.wasProvided("careerStage"));
        var reset = JSON.readValue("{\"title\":\"我的简历\",\"careerStage\":\"UNKNOWN\"}", ResumeProfileUpdateDTO.class);
        assertTrue(reset.wasProvided("careerStage"));
        assertEquals(CareerStage.UNKNOWN, reset.getCareerStage());
        assertThrows(JsonProcessingException.class, () -> JSON.readValue(
                "{\"formalExperienceStatus\":null}", ResumeProfileUpdateDTO.class));
    }

    @Test
    void nullableBooleanAndDatePreserveThreeStates() throws Exception {
        var missing = JSON.readValue("{}", ResumeExperienceSaveDTO.class);
        assertFalse(missing.wasProvided("isCurrent"));
        var ended = JSON.readValue("{\"isCurrent\":false}", ResumeExperienceSaveDTO.class);
        assertTrue(ended.wasProvided("isCurrent"));
        assertEquals(Boolean.FALSE, ended.getIsCurrent());
        var clear = JSON.readValue("{\"isCurrent\":null,\"endDate\":null}", ResumeExperienceSaveDTO.class);
        assertTrue(clear.wasProvided("isCurrent"));
        assertTrue(clear.wasProvided("endDate"));
        assertNull(clear.getIsCurrent());
        assertFalse(clear.wasProvided("startDate"));
    }

    @Test
    void freeTextAndNullableProficiencySurviveDeserialization() throws Exception {
        var dto = JSON.readValue("{\"skillName\":\"熟悉Java\",\"description\":\"能规范编写过程文件\",\"proficiency\":null}", ResumeSkillSaveDTO.class);
        assertTrue(VALIDATOR.validate(dto).isEmpty());
        assertEquals("熟悉Java", dto.getSkillName());
        assertEquals("能规范编写过程文件", dto.getDescription());
        assertTrue(dto.wasProvided("proficiency"));
        assertNull(dto.getProficiency());
        assertFalse(JSON.writeValueAsString(dto).contains("suppliedFields"));
    }

    @Test
    void rejectsOwnershipDerivedFieldsAndPresenceSpoofing() {
        assertThrows(JsonProcessingException.class, () -> JSON.readValue("{\"resumeId\":7}", ResumeSkillSaveDTO.class));
        assertThrows(JsonProcessingException.class, () -> JSON.readValue("{\"jobId\":7}", JobRequiredAbilityDTO.class));
        assertThrows(JsonProcessingException.class, () -> JSON.readValue("{\"requiredAbilitiesSource\":\"STRUCTURED\"}", JobContentSaveDTO.class));
        // 内部标记不能通过请求伪造，额外字段统一拒绝。
        assertThrows(JsonProcessingException.class, () -> JSON.readValue(
                "{\"suppliedFields\":[\"careerStage\"]}", ResumeProfileUpdateDTO.class));
    }

    @Test
    void abilitiesDistinguishMissingNullAndExplicitEmpty() throws Exception {
        var missing = JSON.readValue("{\"title\":\"岗位\"}", JobContentSaveDTO.class);
        assertFalse(missing.wasProvided("requiredAbilities"));
        var keep = JSON.readValue("{\"title\":\"岗位\",\"requiredAbilities\":null}", JobContentSaveDTO.class);
        assertTrue(keep.wasProvided("requiredAbilities"));
        assertNull(keep.getRequiredAbilities());
        assertTrue(VALIDATOR.validate(keep).isEmpty());
        var clear = JSON.readValue("{\"title\":\"岗位\",\"requiredAbilities\":[],\"requiredAbilitiesVersion\":0}", JobContentSaveDTO.class);
        assertTrue(clear.getRequiredAbilities().isEmpty());
        assertTrue(VALIDATOR.validate(clear).isEmpty());
        clear.setRequiredAbilitiesVersion(null);
        assertFalse(VALIDATOR.validate(clear).isEmpty());
    }

    @Test
    void versionsRejectFractionalStringNegativeAndOverflowValues() {
        for (String token : List.of("0.9", "\"0\"", "9223372036854775808")) {
            assertThrows(JsonProcessingException.class, () -> JSON.readValue(
                    "{\"requiredAbilitiesVersion\":" + token + "}", JobContentSaveDTO.class));
        }
        var dto = new JobContentSaveDTO();
        dto.setTitle("岗位");
        dto.setRequiredAbilitiesVersion(-1L);
        assertFalse(VALIDATOR.validate(dto).isEmpty());
    }

    @Test
    void validatesNestedItemsCountAndCombinedProjection() throws Exception {
        var dto = JSON.readValue("{\"title\":\"岗位\",\"requiredAbilitiesVersion\":0,\"requiredAbilities\":[{\"rawText\":\"Java\"},{\"rawText\":\"跨部门沟通\"}]}", JobContentSaveDTO.class);
        assertTrue(VALIDATOR.validate(dto).isEmpty());
        dto.setRequiredSkills("不一致");
        assertFalse(VALIDATOR.validate(dto).isEmpty());
        dto.setRequiredSkills("Java\n跨部门沟通");
        assertTrue(VALIDATOR.validate(dto).isEmpty());
        var longItem = new JobRequiredAbilityDTO();
        longItem.setRawText("a".repeat(300));
        dto = new JobContentSaveDTO();
        dto.setTitle("岗位");
        dto.setRequiredAbilitiesVersion(0L);
        dto.setRequiredAbilities(List.of(longItem, longItem));
        assertFalse(VALIDATOR.validate(dto).isEmpty());
        var shortItem = new JobRequiredAbilityDTO();
        shortItem.setRawText("a");
        dto.setRequiredAbilities(IntStream.rangeClosed(0, RecommendationInputConstants.REQUIRED_ABILITY_MAX_ITEMS)
                .mapToObj(i -> shortItem).toList());
        assertFalse(VALIDATOR.validate(dto).isEmpty());
        assertFalse(VALIDATOR.validate(JSON.readValue("{\"rawText\":\" \"}", JobRequiredAbilityDTO.class)).isEmpty());
    }

    @Test
    void nullableDirectionAndLegacyTextHaveIndependentPresence() throws Exception {
        var pref = JSON.readValue("{\"directionCode\":null}", JobPreferenceSaveDTO.class);
        assertTrue(pref.wasProvided("directionCode"));
        assertNull(pref.getDirectionCode());
        var job = JSON.readValue("{\"requiredSkills\":null}", JobContentSaveDTO.class);
        assertTrue(job.wasProvided("requiredSkills"));
        assertFalse(job.wasProvided("directionCode"));
    }

    @Test
    void responseKeepsNullVersusExplicitEmptyAndOriginalEvidence() throws Exception {
        var job = new Job();
        job.setRequiredAbilitiesSource(RequiredAbilitiesSource.LEGACY_TEXT);
        assertNull(new JobContentDetailVO(job, null).getRequiredAbilities());
        job.setRequiredAbilitiesSource(RequiredAbilitiesSource.STRUCTURED);
        assertTrue(new JobContentDetailVO(job, List.of()).getRequiredAbilities().isEmpty());
        assertThrows(NullPointerException.class, () -> new JobContentDetailVO(job, null));
        var experience = new ResumeExperience();
        experience.setIsCurrent(false);
        experience.setExperienceType(ExperienceType.INTERNSHIP);
        var tree = JSON.valueToTree(new ResumeExperienceVO(experience));
        assertFalse(tree.get("isCurrent").asBoolean());
        assertEquals("INTERNSHIP", tree.get("experienceType").asText());
        var ability = new JobRequiredAbility();
        ability.setRawText("善于跨部门沟通");
        assertEquals("善于跨部门沟通", new JobRequiredAbilityVO(ability).getRawText());
    }
}
