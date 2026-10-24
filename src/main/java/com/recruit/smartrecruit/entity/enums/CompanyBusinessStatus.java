package com.recruit.smartrecruit.entity.enums;

import lombok.Getter;

/** 枚举名称作为数据库与接口代码，description 供展示使用。 */
@Getter
public enum CompanyBusinessStatus {
    EXISTING("存续"),
    OPERATING("在业"),
    OPEN("开业"),
    OTHER("其他"),
    UNCONFIRMED("待核实");

    private final String description;

    CompanyBusinessStatus(String description) {
        this.description = description;
    }
}
