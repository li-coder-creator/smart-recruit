package com.recruit.smartrecruit.service.impl;

import com.recruit.smartrecruit.constant.RecommendationInputConstants;
import com.recruit.smartrecruit.dto.JobContentSaveDTO;
import com.recruit.smartrecruit.entity.Job;
import com.recruit.smartrecruit.entity.enums.JobDirection;
import com.recruit.smartrecruit.entity.enums.RequiredAbilitiesSource;
import com.recruit.smartrecruit.exception.BusinessException;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.stream.Collectors;

/** 只计算允许保存的内容；已有岗位必须先锁定父行，再调用此策略。 */
@Component
public class JobAbilityContentPolicy {
    public void apply(JobContentSaveDTO dto, Job job) {
        boolean creating = job.getId() == null;
        String oldText = job.getRequiredSkills();
        String oldCategory = job.getCategory();
        JobDirection oldDirection = job.getDirectionCode();
        RequiredAbilitiesSource source = creating ? RequiredAbilitiesSource.LEGACY_TEXT : job.getRequiredAbilitiesSource();
        long version = creating ? RecommendationInputConstants.INITIAL_REQUIRED_ABILITIES_VERSION : job.getRequiredAbilitiesVersion();
        boolean replace = dto.getRequiredAbilities() != null;
        String text;
        if (replace) {
            if (!Objects.equals(dto.getRequiredAbilitiesVersion(), version)) {
                throw new BusinessException("能力要求版本已变化，请刷新岗位详情后重试");
            }
            text = dto.getRequiredAbilities().isEmpty() ? null : dto.getRequiredAbilities().stream()
                    .map(item -> item.getRawText()).collect(Collectors.joining(RecommendationInputConstants.REQUIRED_ABILITY_SEPARATOR));
            if (!dto.isAbilityProjectionWithinLimit() || !dto.isAbilityProjectionConsistent()) {
                throw new BusinessException("必备能力展示文本超长或与数组不一致");
            }
            source = RequiredAbilitiesSource.STRUCTURED;
            version = nextVersion(version);
        } else {
            text = dto.wasProvided("requiredSkills") ? dto.getRequiredSkills() : oldText;
            if (source == RequiredAbilitiesSource.STRUCTURED && !Objects.equals(oldText, text)) {
                throw new BusinessException("岗位已使用逐条能力，请通过requiredAbilities编辑");
            }
            if (!creating && !Objects.equals(oldText, text)) {
                version = nextVersion(version);
            }
        }
        JobDirection direction = dto.wasProvided("directionCode") ? dto.getDirectionCode()
                : Objects.equals(oldCategory, dto.getCategory()) ? oldDirection : null;
        if (("后端开发".equals(dto.getCategory()) && direction == JobDirection.FRONTEND)
                || ("前端开发".equals(dto.getCategory()) && direction == JobDirection.BACKEND)) {
            throw new BusinessException("岗位类别与方向代码矛盾");
        }
        dto.applyTo(job);
        // applyTo保留旧表单全量更新约定；唯一来源字段由本策略覆盖，不能被旧文本误清空。
        job.setRequiredSkills(text);
        job.setRequiredAbilitiesSource(source);
        job.setRequiredAbilitiesVersion(version);
        job.setDirectionCode(direction);
    }

    private long nextVersion(long version) {
        if (version == Long.MAX_VALUE) {
            throw new BusinessException("能力要求版本超出范围，请联系管理员");
        }
        return version + 1;
    }
}
