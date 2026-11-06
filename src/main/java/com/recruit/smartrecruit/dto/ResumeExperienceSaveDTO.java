package com.recruit.smartrecruit.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.recruit.smartrecruit.constant.ResumeValidationConstants;
import com.recruit.smartrecruit.dto.support.FieldAwareDTO;
import com.recruit.smartrecruit.entity.enums.ExperienceType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

/** 日期、类型、至今状态先按存在性合并，再由Service校验完整对象。 */
@Getter
@Setter
@NoArgsConstructor
public class ResumeExperienceSaveDTO extends FieldAwareDTO {
    @NotBlank(message = "公司名称不能为空")
    @Size(max = ResumeValidationConstants.MEDIUM_TEXT_MAX_LENGTH, message = "公司名称不能超过{max}个字符")
    private String companyName;
    @NotBlank(message = "职位不能为空")
    @Size(max = ResumeValidationConstants.SHORT_TEXT_MAX_LENGTH, message = "职位不能超过{max}个字符")
    private String position;
    private LocalDate startDate;
    private LocalDate endDate;
    @Size(max = ResumeValidationConstants.SHORT_TEXT_MAX_LENGTH, message = "时间说明不能超过{max}个字符")
    private String periodText;
    @Size(max = ResumeValidationConstants.DETAIL_DESCRIPTION_MAX_LENGTH, message = "经历描述不能超过{max}个字符")
    private String description;
    @Size(max = ResumeValidationConstants.DETAIL_DESCRIPTION_MAX_LENGTH, message = "工作成果不能超过{max}个字符")
    private String achievement;
    @PositiveOrZero(message = "排序值不能小于0")
    private Integer sortOrder;
    private ExperienceType experienceType;
    private Boolean isCurrent;

    public void setStartDate(LocalDate value) {
        markProvided("startDate");
        startDate = value;
    }

    public void setEndDate(LocalDate value) {
        markProvided("endDate");
        endDate = value;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setExperienceType(ExperienceType value) {
        markProvided("experienceType");
        experienceType = value;
    }

    public void setIsCurrent(Boolean value) {
        markProvided("isCurrent");
        isCurrent = value;
    }
}
