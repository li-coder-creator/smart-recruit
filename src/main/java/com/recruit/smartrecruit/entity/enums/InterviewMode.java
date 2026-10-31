package com.recruit.smartrecruit.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

/**
 * 面试方式。
 * 线上与线下信息按契约分别填写。
 */
@Getter
public enum InterviewMode {
    /** 线上面试 */
    ONLINE("线上"),
    /** 线下面试 */
    OFFLINE("线下");

    /**
     * 展示用中文描述
     */
    private final String description;

    InterviewMode(String description) {
        this.description = description;
    }

    /** 只接受枚举名称，避免 JSON 数字被当作枚举下标。 */
    @JsonCreator
    public static InterviewMode fromCode(String code) {
        return code == null ? null : InterviewMode.valueOf(code);
    }
}
