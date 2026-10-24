package com.recruit.smartrecruit.entity.enums;

import lombok.Getter;

/** 枚举名称作为数据库与接口代码，description 供展示使用。 */
@Getter
public enum CompanyReviewType {
    REGISTRATION("工商资料"),
    QUALIFICATION("资质材料");

    private final String description;

    CompanyReviewType(String description) {
        this.description = description;
    }
}
