package com.recruit.smartrecruit.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.recruit.smartrecruit.dto.support.*;
import com.recruit.smartrecruit.recommendation.RecommendationRules;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor
public class RecommendationWeightsDTO extends FieldAwareDTO {
    @NotNull @Min(0) @Max(RecommendationRules.MAX_CUSTOM_WEIGHT) @JsonDeserialize(using=StrictIntegerDeserializer.class)
    @JsonAlias("SKILL") private Integer skill;
    @NotNull @Min(0) @Max(RecommendationRules.MAX_CUSTOM_WEIGHT) @JsonDeserialize(using=StrictIntegerDeserializer.class)
    @JsonAlias("SALARY") private Integer salary;
    @NotNull @Min(0) @Max(RecommendationRules.MAX_CUSTOM_WEIGHT) @JsonDeserialize(using=StrictIntegerDeserializer.class)
    @JsonAlias("CITY") private Integer city;
    @NotNull @Min(0) @Max(RecommendationRules.MAX_CUSTOM_WEIGHT) @JsonDeserialize(using=StrictIntegerDeserializer.class)
    @JsonAlias("EXPERIENCE") private Integer experience;
    @NotNull @Min(0) @Max(RecommendationRules.MAX_CUSTOM_WEIGHT) @JsonDeserialize(using=StrictIntegerDeserializer.class)
    @JsonAlias("EDUCATION") private Integer education;
    @JsonIgnore @AssertTrue(message="自定义权重总和必须大于0")
    public boolean isPositiveSum() {
        if (skill==null||salary==null||city==null||experience==null||education==null) return true;
        return (long)skill+salary+city+experience+education>0;
    }
}
