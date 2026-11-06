package com.recruit.smartrecruit.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/** 字符串代码同时用于数据库与JSON；禁止使用ordinal表示业务含义。 */
@Getter
public enum FormalExperienceStatus {
    NONE("明确无正式工作经历"),
    HAS_EXPERIENCE("有正式工作经历"),
    UNKNOWN("未确认");

    private final String description;

    FormalExperienceStatus(String description) {
        this.description = description;
    }

    @JsonValue
    public String getCode() {
        return name();
    }

    /** 明确拒绝未知代码及数字序号，null由对应DTO的可空约束处理。 */
    @JsonCreator
    public static FormalExperienceStatus fromCode(String code) {
        return valueOf(code);
    }
}
