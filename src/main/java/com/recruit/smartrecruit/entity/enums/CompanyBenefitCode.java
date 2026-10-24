package com.recruit.smartrecruit.entity.enums;

import lombok.Getter;

/** 枚举名称作为数据库与接口代码，description 供展示使用。 */
@Getter
public enum CompanyBenefitCode {
    SOCIAL_INSURANCE_HOUSING_FUND("五险一金"),
    PAID_ANNUAL_LEAVE("带薪年假"),
    HEALTH_CHECK("定期体检"),
    MEAL_ALLOWANCE("餐补"),
    HOLIDAY_BENEFITS("节日福利"),
    YEAR_END_BONUS("年终奖"),
    SUPPLEMENTAL_MEDICAL_INSURANCE("补充医疗保险"),
    TRANSPORT_ALLOWANCE("交通补助"),
    EMPLOYEE_TRAVEL("员工旅游"),
    LEARNING_TRAINING("学习培训"),
    STOCK_OPTIONS("股票期权"),
    FLEXIBLE_WORK("弹性工作");

    private final String description;

    CompanyBenefitCode(String description) {
        this.description = description;
    }
}
