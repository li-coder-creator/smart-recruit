package com.recruit.smartrecruit.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

/**
 * 面试时间展示状态。
 * 按统一服务器时间派生的展示状态，不是出席或面试结果。
 */
@Getter
public enum InterviewTimingStatus {
    /** 待开始 */
    UPCOMING("待开始"),
    /** 进行中 */
    ONGOING("进行中"),
    /** 已过期 */
    EXPIRED("已过期"),
    /** 已取消 */
    CANCELLED("已取消");

    /**
     * 展示用中文描述
     */
    private final String description;

    InterviewTimingStatus(String description) {
        this.description = description;
    }

    /** 只接受枚举名称，避免 JSON 数字被当作枚举下标。 */
    @JsonCreator
    public static InterviewTimingStatus fromCode(String code) {
        return code == null ? null : InterviewTimingStatus.valueOf(code);
    }
}
