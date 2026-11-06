package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.JobRequiredAbility;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;
import com.recruit.smartrecruit.entity.enums.AbilityProficiency;
import java.time.LocalDateTime;

/** 独立响应契约；仅从已鉴权读取的实体复制，不执行查询或推断未知信息。 */
@Data
@NoArgsConstructor
public class JobRequiredAbilityVO {
    private Long id;
    private Long jobId;
    private String rawText;
    private String abilityName;
    private AbilityProficiency proficiency;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public JobRequiredAbilityVO(JobRequiredAbility source) {
        BeanUtils.copyProperties(source, this);
    }
}
