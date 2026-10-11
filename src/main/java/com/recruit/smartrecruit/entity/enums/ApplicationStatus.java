package com.recruit.smartrecruit.entity.enums;

import lombok.Getter;

@Getter
public enum ApplicationStatus {

    PENDING(0, "待处理"),

    SCREENING(1, "筛选中"),

    INTERVIEW(2, "面试"),

    HIRED(3, "录用"),

    REJECTED(4, "拒绝");

    private final Integer code;

    private final String description;

    ApplicationStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /** 允许跳过中间阶段向前处理；同状态、回退及终态变更均不允许。 */
    public boolean canTransitionTo(ApplicationStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == SCREENING || target == INTERVIEW
                    || target == HIRED || target == REJECTED;
            case SCREENING -> target == INTERVIEW || target == HIRED || target == REJECTED;
            case INTERVIEW -> target == HIRED || target == REJECTED;
            case HIRED, REJECTED -> false;
        };
    }

    public static ApplicationStatus fromCode(Integer code) {

        for (ApplicationStatus status : values()) {

            if (status.code.equals(code)) {
                return status;
            }

        }

        throw new IllegalArgumentException(
                "无效的投递状态：" + code
        );
    }
}
