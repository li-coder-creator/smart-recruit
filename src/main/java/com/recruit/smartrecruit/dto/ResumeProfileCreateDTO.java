package com.recruit.smartrecruit.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.recruit.smartrecruit.entity.enums.CareerStage;
import com.recruit.smartrecruit.entity.enums.FormalExperienceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 简历创建入口契约，基础字段复用原DTO，声明由Service在事务中保存。 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ResumeProfileCreateDTO extends ResumeCreateDTO {
    @NotNull(message = "求职阶段不能为null，未确认请传UNKNOWN")
    private CareerStage careerStage = CareerStage.UNKNOWN;
    @NotNull(message = "正式经历确认不能为null，未确认请传UNKNOWN")
    private FormalExperienceStatus formalExperienceStatus = FormalExperienceStatus.UNKNOWN;

    @JsonSetter(nulls = Nulls.FAIL)
    public void setCareerStage(CareerStage value) {
        careerStage = value;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setFormalExperienceStatus(FormalExperienceStatus value) {
        formalExperienceStatus = value;
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("不支持的请求字段：" + field);
    }
}
