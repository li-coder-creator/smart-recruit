package com.recruit.smartrecruit.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

/**
 * 投递事件类型。
 * 枚举名称作为接口和数据库代码。
 */
@Getter
public enum ApplicationEventType {
    /** 投递成功 */
    APPLIED("投递成功"),
    /** 投递状态变更 */
    STATUS_CHANGED("投递状态变更"),
    /** 面试已安排 */
    INTERVIEW_SCHEDULED("面试已安排"),
    /** 面试安排已修改 */
    INTERVIEW_RESCHEDULED("面试安排已修改"),
    /** 面试安排已取消 */
    INTERVIEW_CANCELLED("面试安排已取消");

    /**
     * 展示用中文描述
     */
    private final String description;

    ApplicationEventType(String description) {
        this.description = description;
    }

    /** 只接受枚举名称，避免 JSON 数字被当作枚举下标。 */
    @JsonCreator
    public static ApplicationEventType fromCode(String code) {
        return code == null ? null : ApplicationEventType.valueOf(code);
    }
}
