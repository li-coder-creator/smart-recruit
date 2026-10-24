package com.recruit.smartrecruit.entity.enums;

import lombok.Getter;

/** 枚举名称作为数据库与接口代码，description 供展示使用。 */
@Getter
public enum CompanyReviewStatus {
    PENDING("待审核"),
    APPROVED("审核通过"),
    REJECTED("审核未通过");

    private final String description;

    CompanyReviewStatus(String description) {
        this.description = description;
    }
}
