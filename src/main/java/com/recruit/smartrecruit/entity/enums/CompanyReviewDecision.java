package com.recruit.smartrecruit.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

/** 枚举名称作为数据库与接口代码，description 供展示使用。 */
@Getter
public enum CompanyReviewDecision {
    APPROVE("通过"),
    REJECT("拒绝");

    private final String description;

    CompanyReviewDecision(String description) {
        this.description = description;
    }

    /** 请求必须提交枚举名称，避免数字被 Jackson 当作枚举下标。 */
    @JsonCreator
    public static CompanyReviewDecision fromCode(String code) {
        return CompanyReviewDecision.valueOf(code);
    }
}
