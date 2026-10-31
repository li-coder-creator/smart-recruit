package com.recruit.smartrecruit.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

/**
 * 事件操作者类型。
 * 仅展示角色，不暴露账号 ID。
 */
@Getter
public enum ApplicationActorType {
    /** 求职者 */
    JOB_SEEKER("求职者"),
    /** 企业 */
    COMPANY("企业"),
    /** 系统 */
    SYSTEM("系统");

    /**
     * 展示用中文描述
     */
    private final String description;

    ApplicationActorType(String description) {
        this.description = description;
    }

    /** 只接受枚举名称，避免 JSON 数字被当作枚举下标。 */
    @JsonCreator
    public static ApplicationActorType fromCode(String code) {
        return code == null ? null : ApplicationActorType.valueOf(code);
    }
}
