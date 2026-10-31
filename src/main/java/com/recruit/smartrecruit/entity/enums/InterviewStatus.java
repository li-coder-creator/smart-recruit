package com.recruit.smartrecruit.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

/**
 * 面试存储状态。
 * 时间到期不自动表示已完成。
 */
@Getter
public enum InterviewStatus {
    /** 已安排 */
    SCHEDULED("已安排"),
    /** 已取消 */
    CANCELLED("已取消");

    /**
     * 展示用中文描述
     */
    private final String description;

    InterviewStatus(String description) {
        this.description = description;
    }

    /** 只接受枚举名称，避免 JSON 数字被当作枚举下标。 */
    @JsonCreator
    public static InterviewStatus fromCode(String code) {
        return code == null ? null : InterviewStatus.valueOf(code);
    }
}
