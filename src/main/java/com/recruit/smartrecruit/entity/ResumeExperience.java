package com.recruit.smartrecruit.entity;

import com.recruit.smartrecruit.constant.ResumeValidationConstants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ResumeExperience {
    private Long id;
    private Long resumeId;
    @NotBlank(message = "公司名称不能为空")
    @Size(max = ResumeValidationConstants.MEDIUM_TEXT_MAX_LENGTH, message = "公司名称长度不能超过100个字")
    private String companyName;
    @NotBlank(message = "职位不能为空")
    @Size(max = ResumeValidationConstants.SHORT_TEXT_MAX_LENGTH, message = "职位长度不能超过50个字")
    private String position;
    private LocalDate startDate;
    private LocalDate endDate;
    @Size(max = ResumeValidationConstants.SHORT_TEXT_MAX_LENGTH, message = "时间说明长度不能超过50个字")
    private String periodText;
    @Size(max = ResumeValidationConstants.DETAIL_DESCRIPTION_MAX_LENGTH, message = "工作经历描述长度不能超过2000个字")
    private String description;
    @Size(max = ResumeValidationConstants.DETAIL_DESCRIPTION_MAX_LENGTH, message = "工作成果长度不能超过2000个字")
    private String achievement;
    @PositiveOrZero(message = "排序值不能小于0")
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
