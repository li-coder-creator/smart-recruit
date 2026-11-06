package com.recruit.smartrecruit.dto;

import com.recruit.smartrecruit.constant.ResumeValidationConstants;
import com.recruit.smartrecruit.dto.support.FieldAwareDTO;
import com.recruit.smartrecruit.entity.enums.AbilityProficiency;
import jakarta.validation.constraints.*;
import lombok.*;

/** 原文保留；名称规范化及程度冲突处理属于后续特征/业务层。 */
@Getter
@Setter
@NoArgsConstructor
public class ResumeSkillSaveDTO extends FieldAwareDTO {
    @NotBlank(message = "技能名称不能为空")
    @Size(max = ResumeValidationConstants.SHORT_TEXT_MAX_LENGTH, message = "技能名称不能超过{max}个字符")
    private String skillName;
    @Size(max = ResumeValidationConstants.DESCRIPTION_MAX_LENGTH, message = "技能说明不能超过{max}个字符")
    private String description;
    @PositiveOrZero(message = "排序值不能小于0")
    private Integer sortOrder;
    private AbilityProficiency proficiency;

    public void setProficiency(AbilityProficiency value) {
        markProvided("proficiency");
        proficiency = value;
    }
}
