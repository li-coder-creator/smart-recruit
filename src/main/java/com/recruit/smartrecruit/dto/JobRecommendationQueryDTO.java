package com.recruit.smartrecruit.dto;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.recruit.smartrecruit.constant.*;
import com.recruit.smartrecruit.dto.support.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor
public class JobRecommendationQueryDTO extends FieldAwareDTO {
    public enum Preset {
        BALANCED, SALARY_FIRST, CITY_FIRST;
        @JsonCreator public static Preset fromCode(String code) { return valueOf(code); }
    }
    public enum Sort {
        MATCH, SALARY_DESC;
        @JsonCreator public static Sort fromCode(String code) { return valueOf(code); }
    }
    @NotNull(message="请选择简历") @Positive @JsonDeserialize(using=StrictLongDeserializer.class)
    private Long resumeId;
    @Valid private RecommendationPreferencesDTO preferences;
    private Preset preset;
    @Valid private RecommendationWeightsDTO weights;
    @NotNull @JsonDeserialize(using=StrictBooleanDeserializer.class)
    private Boolean strictCity=RecommendationQueryConstants.DEFAULT_STRICT_CITY;
    @NotNull @JsonDeserialize(using=StrictBooleanDeserializer.class)
    private Boolean excludeApplied=RecommendationQueryConstants.DEFAULT_EXCLUDE_APPLIED;
    @NotNull @JsonDeserialize(using=StrictBooleanDeserializer.class)
    private Boolean excludeExpired=RecommendationQueryConstants.DEFAULT_EXCLUDE_EXPIRED;
    @NotNull @Min(JobValidationConstants.MIN_PAGE_NUMBER) @JsonDeserialize(using=StrictIntegerDeserializer.class)
    private Integer page=RecommendationQueryConstants.DEFAULT_PAGE;
    @NotNull @Min(JobValidationConstants.MIN_PAGE_SIZE) @Max(JobValidationConstants.MAX_PAGE_SIZE) @JsonDeserialize(using=StrictIntegerDeserializer.class)
    private Integer size=RecommendationQueryConstants.DEFAULT_SIZE;
    @NotNull private Sort sort=Sort.MATCH;
    @JsonIgnore @AssertTrue(message="预设与自定义权重只能选择一种")
    public boolean isWeightSelectionValid() { return preset==null||weights==null; }
}
