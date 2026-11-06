package com.recruit.smartrecruit.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.recruit.smartrecruit.constant.JobValidationConstants;
import com.recruit.smartrecruit.dto.support.*;
import com.recruit.smartrecruit.entity.enums.JobDirection;
import jakarta.validation.constraints.*;
import lombok.*;

/** 只覆盖本次查询；省略继承简历默认，显式null清空可选项。 */
@Getter @Setter @NoArgsConstructor
public class RecommendationPreferencesDTO extends FieldAwareDTO {
    private JobDirection directionCode;
    @Size(max=JobValidationConstants.CITY_MAX_LENGTH, message="期望城市不能超过50个字符")
    private String city;
    @Positive(message="期望月薪下限必须为正数") @JsonDeserialize(using=StrictIntegerDeserializer.class)
    private Integer salaryMin;
    @Positive(message="期望月薪上限必须为正数") @JsonDeserialize(using=StrictIntegerDeserializer.class)
    private Integer salaryMax;
    public void setDirectionCode(JobDirection value) { markProvided("directionCode"); directionCode=value; }
    public void setCity(String value) { markProvided("city"); city=value; }
    public void setSalaryMin(Integer value) { markProvided("salaryMin"); salaryMin=value; }
    public void setSalaryMax(Integer value) { markProvided("salaryMax"); salaryMax=value; }
}
