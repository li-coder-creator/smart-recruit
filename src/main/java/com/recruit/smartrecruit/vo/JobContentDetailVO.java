package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.Job;
import com.recruit.smartrecruit.entity.JobRequiredAbility;
import com.recruit.smartrecruit.entity.enums.JobDirection;
import com.recruit.smartrecruit.entity.enums.RequiredAbilitiesSource;
import lombok.*;

import java.util.List;
import java.util.Objects;

/** 复用旧详情字段，调用方需在同一读取快照中加载对应的能力列表。 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class JobContentDetailVO extends JobDetailVO {
    private JobDirection directionCode;
    private RequiredAbilitiesSource requiredAbilitiesSource;
    private Long requiredAbilitiesVersion;
    /** LEGACY_TEXT为null；STRUCTURED明确无条目为[]，不从空列表猜测来源。 */
    private List<JobRequiredAbilityVO> requiredAbilities;

    public JobContentDetailVO(Job job, List<JobRequiredAbility> loadedAbilities) {
        super(job);
        directionCode = job.getDirectionCode();
        requiredAbilitiesSource = job.getRequiredAbilitiesSource();
        requiredAbilitiesVersion = job.getRequiredAbilitiesVersion();
        if (requiredAbilitiesSource == RequiredAbilitiesSource.STRUCTURED) {
            requiredAbilities = Objects.requireNonNull(loadedAbilities, "必须加载结构化能力列表")
                    .stream().map(JobRequiredAbilityVO::new).toList();
        }
    }
}
