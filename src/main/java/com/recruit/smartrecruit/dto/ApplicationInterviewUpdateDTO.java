package com.recruit.smartrecruit.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.recruit.smartrecruit.constant.ApplicationValidationConstants;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 面试修改请求参数。
 * 继承创建参数，额外携带并发版本号用于乐观锁校验。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ApplicationInterviewUpdateDTO extends ApplicationInterviewCreateDTO {
    /**
     * 面试并发版本号，用于乐观锁校验，防止并发覆盖修改
     */
    @NotNull(message = "面试版本不能为空")
    @Min(value = ApplicationValidationConstants.MIN_VERSION, message = "面试版本不能小于0")
    @JsonDeserialize(using = InterviewVersionDeserializer.class)
    private Long version;
}
