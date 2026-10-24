package com.recruit.smartrecruit.entity.enums;

import lombok.Getter;

/** 枚举名称作为数据库与接口代码，description 供展示使用。 */
@Getter
public enum CompanyEmployeeSize {
    UNDER_20("20人以下"),
    FROM_20_TO_99("20–99人"),
    FROM_100_TO_499("100–499人"),
    FROM_500_TO_999("500–999人"),
    FROM_1000_TO_9999("1000–9999人"),
    AT_LEAST_10000("10000人以上");

    private final String description;

    CompanyEmployeeSize(String description) {
        this.description = description;
    }
}
