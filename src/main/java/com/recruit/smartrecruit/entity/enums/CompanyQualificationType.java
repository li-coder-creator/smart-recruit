package com.recruit.smartrecruit.entity.enums;

import lombok.Getter;

/** 枚举名称作为数据库与接口代码，description 供展示使用。 */
@Getter
public enum CompanyQualificationType {
    BUSINESS_LICENSE("营业执照"),
    INDUSTRY_CERTIFICATE("行业资质"),
    OTHER("其他材料");

    private final String description;

    CompanyQualificationType(String description) {
        this.description = description;
    }
}
