package com.recruit.smartrecruit.constant;

/** 企业档案参数约束，字段容量与企业资料迁移保持一致。 */
public final class CompanyValidationConstants {
    public static final int NAME_MAX_LENGTH = 100;
    public static final int FULL_NAME_MAX_LENGTH = 200;
    public static final int WEBSITE_MAX_LENGTH = 255;
    public static final int CITY_MAX_LENGTH = 50;
    public static final int ADDRESS_MAX_LENGTH = 255;
    public static final int COMMUTE_DESCRIPTION_MAX_LENGTH = 500;
    public static final int DESCRIPTION_MAX_LENGTH = 2000;
    public static final int LEGAL_REPRESENTATIVE_MAX_LENGTH = 100;
    public static final int REGISTERED_CAPITAL_MAX_LENGTH = 100;
    public static final int CREDIT_CODE_MAX_LENGTH = 18;
    public static final int REGISTERED_ADDRESS_MAX_LENGTH = 255;
    public static final int BUSINESS_SCOPE_MAX_LENGTH = 2000;
    public static final int WORK_NOTES_MAX_LENGTH = 2000;
    public static final int PRODUCT_NAME_MAX_LENGTH = 200;
    public static final int PRODUCT_DESCRIPTION_MAX_LENGTH = 2000;
    public static final int TEAM_NAME_MAX_LENGTH = 200;
    public static final int TEAM_ROLE_MAX_LENGTH = 100;
    public static final int TEAM_BIO_MAX_LENGTH = 2000;
    /** 与当前 CompanyBenefitCode 的十二项福利保持同步。 */
    public static final int BENEFITS_MAX_COUNT = 12;
    public static final long MIN_PROFILE_VERSION = 0L;
    public static final String CREDIT_CODE_PATTERN = "^[0-9A-Z]{18}$";
    public static final String HTTP_WEBSITE_PATTERN = "(?i)^https?://.+$";

    private CompanyValidationConstants() {
    }
}
