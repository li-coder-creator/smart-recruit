package com.recruit.smartrecruit.entity;

import com.recruit.smartrecruit.entity.enums.AbilityProficiency;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 岗位纯从属能力要求；不保存模型生成的名称或向量。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobRequiredAbility {
    private Long id;
    private Long jobId;
    private String rawText;
    private String abilityName;
    private AbilityProficiency proficiency;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
