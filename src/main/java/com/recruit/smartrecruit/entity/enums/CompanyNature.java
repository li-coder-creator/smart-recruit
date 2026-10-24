package com.recruit.smartrecruit.entity.enums;

import lombok.Getter;

/** 枚举名称作为数据库与接口代码，description 供展示使用。 */
@Getter
public enum CompanyNature {
    PRIVATE("民营企业"),
    STATE_OWNED("国有企业"),
    FOREIGN_OWNED("外资企业"),
    JOINT_VENTURE("合资企业"),
    PUBLIC_INSTITUTION("事业单位"),
    OTHER("其他");

    private final String description;

    CompanyNature(String description) {
        this.description = description;
    }
}
