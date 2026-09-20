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
public class ResumeProject {
    private Long id;
    private Long resumeId;
    @NotBlank(message = "项目名称不能为空")
    @Size(max = ResumeValidationConstants.MEDIUM_TEXT_MAX_LENGTH, message = "项目名称长度不能超过100个字")
    private String projectName;
    @NotBlank(message = "项目角色不能为空")
    @Size(max = ResumeValidationConstants.SHORT_TEXT_MAX_LENGTH, message = "项目角色长度不能超过50个字")
    private String role;
    private LocalDate startDate;
    private LocalDate endDate;
    @Size(max = ResumeValidationConstants.SHORT_TEXT_MAX_LENGTH, message = "时间说明长度不能超过50个字")
    private String periodText;
    @Size(max = ResumeValidationConstants.TECH_STACK_MAX_LENGTH, message = "技术栈长度不能超过500个字")
    private String techStack;
    @NotBlank(message = "项目描述不能为空")
    @Size(max = ResumeValidationConstants.DETAIL_DESCRIPTION_MAX_LENGTH, message = "项目描述长度不能超过2000个字")
    private String description;
    @Size(max = ResumeValidationConstants.DETAIL_DESCRIPTION_MAX_LENGTH, message = "项目职责长度不能超过2000个字")
    private String responsibility;
    @Size(max = ResumeValidationConstants.DETAIL_DESCRIPTION_MAX_LENGTH, message = "项目成果长度不能超过2000个字")
    private String achievement;
    @PositiveOrZero(message = "排序值不能小于0")
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}


