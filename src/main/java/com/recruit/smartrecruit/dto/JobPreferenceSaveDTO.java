package com.recruit.smartrecruit.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.recruit.smartrecruit.constant.ResumeValidationConstants;
import com.recruit.smartrecruit.dto.support.FieldAwareDTO;
import com.recruit.smartrecruit.entity.enums.JobDirection;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

/** 方向代码不从任意职位文本猜测；明确映射及变更失效由Service处理。 */
@Getter
@Setter
@NoArgsConstructor
public class JobPreferenceSaveDTO extends FieldAwareDTO {
    @NotBlank(message = "期望职位不能为空")
    @Size(max = ResumeValidationConstants.SHORT_TEXT_MAX_LENGTH, message = "期望职位不能超过{max}个字符")
    private String expectedPosition;
    @Size(max = ResumeValidationConstants.CITY_MAX_LENGTH, message = "期望城市不能超过{max}个字符")
    private String expectedCity;
    @PositiveOrZero(message = "薪资下限不能小于0")
    private Integer salaryMin;
    @PositiveOrZero(message = "薪资上限不能小于0")
    private Integer salaryMax;
    @Size(max = ResumeValidationConstants.SHORT_TEXT_MAX_LENGTH, message = "薪资说明不能超过{max}个字符")
    private String salaryText;
    private LocalDate availableDate;
    @Size(max = ResumeValidationConstants.DESCRIPTION_MAX_LENGTH, message = "求职意向说明不能超过{max}个字符")
    private String description;
    private JobDirection directionCode;

    public void setDirectionCode(JobDirection value) {
        markProvided("directionCode");
        directionCode = value;
    }

    /** 兼容现有前端展开表单带出的id；归属与目标记录只使用路径和数据库。 */
    @JsonSetter("id")
    public void ignoreLegacyId(Long ignored) {
    }
}
