package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.ResumeSkill;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;
import com.recruit.smartrecruit.entity.enums.AbilityProficiency;
import java.time.LocalDateTime;

/** 独立响应契约；仅从已鉴权读取的实体复制，不执行查询或推断未知信息。 */
@Data
@NoArgsConstructor
public class ResumeSkillVO {
    private Long id;
    private Long resumeId;
    private String skillName;
    private String description;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private AbilityProficiency proficiency;

    public ResumeSkillVO(ResumeSkill source) {
        BeanUtils.copyProperties(source, this);
    }
}
