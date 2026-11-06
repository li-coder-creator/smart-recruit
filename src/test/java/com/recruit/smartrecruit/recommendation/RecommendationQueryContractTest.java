package com.recruit.smartrecruit.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.smartrecruit.dto.JobRecommendationQueryDTO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class RecommendationQueryContractTest {
    final ObjectMapper json=new ObjectMapper();
    boolean valid(String text) throws Exception {
        try(var factory=Validation.buildDefaultValidatorFactory()) {return factory.getValidator().validate(json.readValue(text,JobRecommendationQueryDTO.class)).isEmpty();}
    }
    @Test void defaultFiltersAndPageMatchAgreedContract() throws Exception {
        var dto=json.readValue("{\"resumeId\":901}",JobRecommendationQueryDTO.class);
        assertTrue(dto.getExcludeApplied());assertTrue(dto.getExcludeExpired());assertFalse(dto.getStrictCity());assertEquals(1,dto.getPage());assertEquals(10,dto.getSize());assertTrue(valid("{\"resumeId\":901}"));
    }
    @ParameterizedTest @ValueSource(strings={
            "{}","{\"resumeId\":0}","{\"resumeId\":-1}","{\"resumeId\":901,\"page\":0}","{\"resumeId\":901,\"size\":101}",
            "{\"resumeId\":901,\"strictCity\":null}","{\"resumeId\":901,\"excludeApplied\":null}","{\"resumeId\":901,\"excludeExpired\":null}",
            "{\"resumeId\":901,\"sort\":null}","{\"resumeId\":901,\"preferences\":{\"salaryMax\":0}}",
            "{\"resumeId\":901,\"weights\":{\"skill\":0,\"salary\":0,\"city\":0,\"experience\":0,\"education\":0}}",
            "{\"resumeId\":901,\"preset\":\"BALANCED\",\"weights\":{\"skill\":1,\"salary\":1,\"city\":1,\"experience\":1,\"education\":1}}",
            "{\"resumeId\":901,\"weights\":{\"skill\":35}}"})
    void beanValidationRejectsInvalidContract(String text) throws Exception {assertFalse(valid(text));}
    @ParameterizedTest @ValueSource(strings={
            "{\"resumeId\":\"901\"}","{\"resumeId\":901.5}","{\"resumeId\":901,\"page\":1.5}","{\"resumeId\":901,\"page\":\"1\"}",
            "{\"resumeId\":901,\"strictCity\":\"false\"}","{\"resumeId\":901,\"excludeExpired\":1}","{\"resumeId\":901,\"sort\":0}",
            "{\"resumeId\":901,\"preset\":0}","{\"resumeId\":901,\"preset\":\"BAD\"}","{\"resumeId\":901,\"userId\":102}",
            "{\"resumeId\":901,\"preferences\":{\"directionCode\":0}}","{\"resumeId\":901,\"preferences\":{\"salaryMax\":\"20000\"}}",
            "{\"resumeId\":901,\"preferences\":{\"id\":1}}","{\"resumeId\":901,\"weights\":{\"skill\":35.5}}",
            "{\"resumeId\":901,\"weights\":{\"unknown\":1}}","{\"resumeId\":901,\"asOfDate\":\"2026-01-01\"}"})
    void jsonDoesNotCoerceNumbersBooleansEnumsOrDerivedFields(String text) {assertThrows(Exception.class,()->json.readValue(text,JobRecommendationQueryDTO.class));}
    @Test void overrideNullIsDifferentFromOmission() throws Exception {
        var dto=json.readValue("{\"resumeId\":901,\"preferences\":{\"city\":null,\"directionCode\":null}}",JobRecommendationQueryDTO.class);
        assertTrue(dto.getPreferences().wasProvided("city"));assertTrue(dto.getPreferences().wasProvided("directionCode"));assertFalse(dto.getPreferences().wasProvided("salaryMax"));
    }
    @Test void canonicalCityCodeCanBeSentBackFromOptions() {assertEquals("XIAN",new RuleDictionary().city("XIAN"));}
    @Test void weightsFromOptionsCanBeSubmittedWithoutCaseConversion() throws Exception {
        assertTrue(valid("{\"resumeId\":901,\"weights\":{\"SKILL\":35,\"SALARY\":25,\"CITY\":20,\"EXPERIENCE\":10,\"EDUCATION\":10}}"));
    }
}
