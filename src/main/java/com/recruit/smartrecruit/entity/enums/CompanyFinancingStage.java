package com.recruit.smartrecruit.entity.enums;

import lombok.Getter;

/** 枚举名称作为数据库与接口代码，description 供展示使用。 */
@Getter
public enum CompanyFinancingStage {
    UNFUNDED("未融资"),
    ANGEL("天使轮"),
    SERIES_A("A轮"),
    SERIES_B("B轮"),
    SERIES_C("C轮"),
    SERIES_D_OR_LATER("D轮及以上"),
    LISTED("已上市"),
    NOT_REQUIRED("不需要融资"),
    UNDISCLOSED("暂不披露");

    private final String description;

    CompanyFinancingStage(String description) {
        this.description = description;
    }
}
