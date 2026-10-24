package com.recruit.smartrecruit.entity.enums;

import lombok.Getter;

/** 枚举名称作为数据库与接口代码，description 供展示使用。 */
@Getter
public enum CompanyOvertimePolicy {
    PROJECT_BASED("按项目情况安排"),
    USUALLY_NONE("通常不加班"),
    OCCASIONAL("偶尔加班"),
    JOB_SPECIFIC("以岗位为准");

    private final String description;

    CompanyOvertimePolicy(String description) {
        this.description = description;
    }
}
