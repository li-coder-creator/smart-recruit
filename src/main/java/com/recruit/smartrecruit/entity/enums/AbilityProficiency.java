package com.recruit.smartrecruit.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/** 字符串代码同时用于数据库与JSON；禁止使用ordinal表示业务含义。 */
@Getter
public enum AbilityProficiency {
    AWARE("了解"),
    FAMILIAR("熟悉"),
    PROFICIENT("熟练"),
    EXPERT("精通");

    private final String description;

    AbilityProficiency(String description) {
        this.description = description;
    }

    @JsonValue
    public String getCode() {
        return name();
    }

    /** 明确拒绝未知代码及数字序号，null由对应DTO的可空约束处理。 */
    @JsonCreator
    public static AbilityProficiency fromCode(String code) {
        return valueOf(code);
    }
}
