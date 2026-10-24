package com.recruit.smartrecruit.entity.enums;

import lombok.Getter;

/** 枚举名称作为数据库与接口代码，description 供展示使用。 */
@Getter
public enum CompanyRestPolicy {
    TWO_DAYS_OFF("双休"),
    ONE_DAY_OFF("单休"),
    ALTERNATING_WEEKENDS("大小周"),
    SHIFT_BASED("排班轮休"),
    JOB_SPECIFIC("以岗位为准");

    private final String description;

    CompanyRestPolicy(String description) {
        this.description = description;
    }
}
