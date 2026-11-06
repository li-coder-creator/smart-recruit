package com.recruit.smartrecruit.dto;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.recruit.smartrecruit.constant.JobValidationConstants;
import com.recruit.smartrecruit.constant.RecommendationInputConstants;
import com.recruit.smartrecruit.dto.support.IntegerVersionDeserializer;
import com.recruit.smartrecruit.entity.enums.JobDirection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.*;
import java.util.stream.Collectors;

/** 岗位内容写入口；复用旧表单字段，并区分能力数组省略、null与显式清空。 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class JobContentSaveDTO extends JobSaveDTO {
    private JobDirection directionCode;
    @Valid
    @Size(max = RecommendationInputConstants.REQUIRED_ABILITY_MAX_ITEMS, message = "必备能力最多{max}项")
    private List<@NotNull(message = "能力要求条目不能为null") JobRequiredAbilityDTO> requiredAbilities;
    @PositiveOrZero(message = "能力要求版本不能小于0")
    @JsonDeserialize(using = IntegerVersionDeserializer.class)
    private Long requiredAbilitiesVersion;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Set<String> suppliedFields = new HashSet<>();

    public void setDirectionCode(JobDirection value) {
        suppliedFields.add("directionCode");
        directionCode = value;
    }

    public void setRequiredAbilities(List<JobRequiredAbilityDTO> value) {
        suppliedFields.add("requiredAbilities");
        requiredAbilities = value;
    }

    @Override
    public void setRequiredSkills(String value) {
        suppliedFields.add("requiredSkills");
        super.setRequiredSkills(value);
    }

    public void setRequiredAbilitiesVersion(Long value) {
        suppliedFields.add("requiredAbilitiesVersion");
        requiredAbilitiesVersion = value;
    }

    @JsonIgnore
    public boolean wasProvided(String field) {
        return suppliedFields.contains(field);
    }

    @JsonIgnore
    @AssertTrue(message = "替换必备能力时必须提交预期版本")
    public boolean isAbilityVersionProvided() {
        return requiredAbilities == null || requiredAbilitiesVersion != null;
    }

    @JsonIgnore
    @AssertTrue(message = "必备能力合并展示文本不能超过500个字符")
    public boolean isAbilityProjectionWithinLimit() {
        String text = projection();
        return text == null || text.length() <= JobValidationConstants.SKILLS_MAX_LENGTH;
    }

    @JsonIgnore
    @AssertTrue(message = "必备能力数组与必备技能展示文本不一致")
    public boolean isAbilityProjectionConsistent() {
        return requiredAbilities == null || !wasProvided("requiredSkills")
                || Objects.equals(getRequiredSkills(), projection());
    }

    /** null表示未替换；空数组的兼容文本为null。对条目本身的错误交给嵌套校验。 */
    private String projection() {
        if (requiredAbilities == null || requiredAbilities.isEmpty()) {
            return null;
        }
        if (requiredAbilities.stream().anyMatch(item -> item == null || item.getRawText() == null)) {
            return null;
        }
        return requiredAbilities.stream().map(JobRequiredAbilityDTO::getRawText)
                .collect(Collectors.joining(RecommendationInputConstants.REQUIRED_ABILITY_SEPARATOR));
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("不支持的请求字段：" + field);
    }
}
