package com.recruit.smartrecruit.entity;


import com.recruit.smartrecruit.constant.ResumeValidationConstants;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ResumeSkill {
    private Long id;
    private Long resumeId;
    @NotBlank(message = "技能名称不能为空")
    @Size(max = ResumeValidationConstants.SHORT_TEXT_MAX_LENGTH, message = "技能名称长度不能超过50个字")
    private String skillName;
    @Size(max = ResumeValidationConstants.DESCRIPTION_MAX_LENGTH, message = "技能描述长度不能超过1000个字")
    private String description;
    @PositiveOrZero(message = "排序值不能小于0")
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
