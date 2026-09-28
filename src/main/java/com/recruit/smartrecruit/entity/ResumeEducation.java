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
public class ResumeEducation {
    private Long id;
    private Long resumeId;
    @NotBlank(message = "学校名称不能为空")
    @Size(max = ResumeValidationConstants.MEDIUM_TEXT_MAX_LENGTH, message = "学校名称长度不能超过100个字")
    private String schoolName;
    @NotBlank(message = "学位不能为空")
    @Size(max = ResumeValidationConstants.DEGREE_MAX_LENGTH, message = "学历长度不能超过30个字")
    private String degree;
    @NotBlank(message = "专业不能为空")
    @Size(max = ResumeValidationConstants.MEDIUM_TEXT_MAX_LENGTH, message = "专业长度不能超过100个字")
    private String major;
    private LocalDate startDate;
    private LocalDate endDate;
    @Size(max = ResumeValidationConstants.GPA_MAX_LENGTH, message = "GPA长度不能超过20个字")
    private String gpa;
    @Size(max = ResumeValidationConstants.SHORT_TEXT_MAX_LENGTH, message = "排名信息长度不能超过50个字")
    private String rankInfo;
    @Size(max = ResumeValidationConstants.DESCRIPTION_MAX_LENGTH, message = "教育经历描述长度不能超过500个字")
    private String description;
    @PositiveOrZero(message = "排序值不能小于0")
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
