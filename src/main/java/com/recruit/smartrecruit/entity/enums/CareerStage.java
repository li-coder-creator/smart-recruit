package com.recruit.smartrecruit.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/** 字符串代码同时用于数据库与JSON；禁止使用ordinal表示业务含义。 */
@Getter
public enum CareerStage {
    STUDENT("在校生"),
    NEW_GRAD("应届毕业生"),
    SOCIAL("社会求职"),
    UNKNOWN("未确认");

    private final String description;

    CareerStage(String description) {
        this.description = description;
    }

    @JsonValue
    public String getCode() {
        return name();
    }

    /** 明确拒绝未知代码及数字序号，null由对应DTO的可空约束处理。 */
    @JsonCreator
    public static CareerStage fromCode(String code) {
        return valueOf(code);
    }
}
