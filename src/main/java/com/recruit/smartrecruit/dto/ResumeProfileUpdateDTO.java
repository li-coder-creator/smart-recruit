package com.recruit.smartrecruit.dto;

import com.fasterxml.jackson.annotation.*;
import com.recruit.smartrecruit.entity.enums.CareerStage;
import com.recruit.smartrecruit.entity.enums.FormalExperienceStatus;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/** 未传声明保留，显式UNKNOWN重置；跨经历的一致性必须由Service在父行锁内校验。 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ResumeProfileUpdateDTO extends ResumeBasicUpdateDTO {
    private CareerStage careerStage;
    private FormalExperienceStatus formalExperienceStatus;
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Set<String> suppliedFields = new HashSet<>();

    @JsonSetter(nulls = Nulls.FAIL)
    public void setCareerStage(CareerStage value) {
        suppliedFields.add("careerStage");
        careerStage = value;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setFormalExperienceStatus(FormalExperienceStatus value) {
        suppliedFields.add("formalExperienceStatus");
        formalExperienceStatus = value;
    }

    @JsonIgnore
    public boolean wasProvided(String field) {
        return suppliedFields.contains(field);
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("不支持的请求字段：" + field);
    }
}
