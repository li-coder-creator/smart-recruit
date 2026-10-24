package com.recruit.smartrecruit.entity.enums;

import lombok.Getter;

/** 枚举名称作为数据库与接口代码，description 供展示使用。 */
@Getter
public enum CompanyIndustry {
    ENTERPRISE_SERVICES("企业服务"),
    INTERNET("互联网"),
    SOFTWARE("计算机软件"),
    E_COMMERCE("电子商务"),
    ARTIFICIAL_INTELLIGENCE("人工智能"),
    SMART_HARDWARE("智能硬件"),
    MANUFACTURING("制造业"),
    EDUCATION("教育培训"),
    LIFE_SERVICES("生活服务"),
    OTHER("其他");

    private final String description;

    CompanyIndustry(String description) {
        this.description = description;
    }
}
