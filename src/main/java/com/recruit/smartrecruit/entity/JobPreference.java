package com.recruit.smartrecruit.entity;

import com.recruit.smartrecruit.constant.ResumeValidationConstants;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class JobPreference {
    private Long id;
    private Long resumeId;
    @NotBlank(message = "期望职位不能为空")
    @Size(max = ResumeValidationConstants.SHORT_TEXT_MAX_LENGTH, message = "期望职位长度不能超过50个字")
    private String expectedPosition;
    @Size(max = ResumeValidationConstants.CITY_MAX_LENGTH, message = "期望城市长度不能超过50个字")
    private String expectedCity;
    @PositiveOrZero(message = "期望薪资最低值不能小于0")
    private Integer salaryMin;
    @PositiveOrZero(message = "期望薪资最高值不能小于0")
    private Integer salaryMax;
    @Size(max = ResumeValidationConstants.SHORT_TEXT_MAX_LENGTH, message = "期望薪资说明长度不能超过50个字")
    private String salaryText;
    private LocalDate availableDate;
    @Size(max = ResumeValidationConstants.DESCRIPTION_MAX_LENGTH, message = "求职意向描述长度不能超过500个字")
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
