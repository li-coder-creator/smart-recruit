package com.recruit.smartrecruit.dto;

import com.recruit.smartrecruit.constant.JobValidationConstants;
import com.recruit.smartrecruit.constant.ResumeValidationConstants;
import com.recruit.smartrecruit.dto.support.FieldAwareDTO;
import com.recruit.smartrecruit.entity.enums.AbilityProficiency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/** 不接收ID、jobId、标准code、排序或模型评分，顺序由数组位置决定。 */
@Getter
@Setter
@NoArgsConstructor
public class JobRequiredAbilityDTO extends FieldAwareDTO {
    @NotBlank(message = "能力要求原文不能为空")
    @Size(max = JobValidationConstants.SKILLS_MAX_LENGTH, message = "能力要求不能超过{max}个字符")
    private String rawText;
    @Size(max = ResumeValidationConstants.SHORT_TEXT_MAX_LENGTH, message = "能力名称不能超过{max}个字符")
    private String abilityName;
    private AbilityProficiency proficiency;
}
