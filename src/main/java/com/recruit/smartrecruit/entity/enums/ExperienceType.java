package com.recruit.smartrecruit.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/** 字符串代码同时用于数据库与JSON；禁止使用ordinal表示业务含义。 */
@Getter
public enum ExperienceType {
    FORMAL("正式工作"),
    INTERNSHIP("实习"),
    OTHER("明确非正式的其他经历"),
    UNKNOWN("未确认");

    private final String description;

    ExperienceType(String description) {
        this.description = description;
    }

    @JsonValue
    public String getCode() {
        return name();
    }

    /** 明确拒绝未知代码及数字序号，null由对应DTO的可空约束处理。 */
    @JsonCreator
    public static ExperienceType fromCode(String code) {
        return valueOf(code);
    }
}
