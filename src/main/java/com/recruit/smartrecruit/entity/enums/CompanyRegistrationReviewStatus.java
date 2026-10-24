package com.recruit.smartrecruit.entity.enums;

import lombok.Getter;

/** 枚举名称作为数据库与接口代码，description 供展示使用。 */
@Getter
public enum CompanyRegistrationReviewStatus {
    UNVERIFIED("未核验"),
    PENDING("待审核"),
    VERIFIED("已核验"),
    REJECTED("审核未通过");

    private final String description;

    CompanyRegistrationReviewStatus(String description) {
        this.description = description;
    }
}
