package com.recruit.smartrecruit.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.recruit.smartrecruit.constant.ApplicationValidationConstants;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 面试取消请求参数。
 * 用于所属的已审核企业取消面试时提交取消原因及并发版本号。
 */
@Data
public class ApplicationInterviewCancelDTO {
    /**
     * 面试并发版本号，用于乐观锁校验，防止并发重复取消
     */
    @NotNull(message = "面试版本不能为空")
    @Min(value = ApplicationValidationConstants.MIN_VERSION, message = "面试版本不能小于0")
    @JsonDeserialize(using = InterviewVersionDeserializer.class)
    private Long version;
    /**
     * 取消原因，必填，最长500字符，提交前自动去除首尾空白
     */
    @NotBlank(message = "取消原因不能为空")
    @Size(max = ApplicationValidationConstants.CANCEL_REASON_MAX_LENGTH, message = "取消原因不能超过500字符")
    private String reason;

    public void setReason(String value) {
        reason = value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * 拒绝未定义的额外字段，保证入参契约严格
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("不支持的取消参数字段");
    }
}
