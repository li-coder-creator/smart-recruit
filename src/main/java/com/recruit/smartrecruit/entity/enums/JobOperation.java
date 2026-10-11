package com.recruit.smartrecruit.entity.enums;

import lombok.Getter;

/** 岗位业务时间线的操作编码；数据库保存枚举名称。 */
@Getter
public enum JobOperation {
    CREATE_DRAFT("保存草稿"),
    PUBLISH("发布岗位"),
    UPDATE("更新岗位信息"),
    PAUSE("暂停招聘"),
    RESUME("恢复招聘"),
    END("结束招聘");

    private final String description;

    JobOperation(String description) {
        this.description = description;
    }

    public static JobOperation fromCode(String code) {
        return JobOperation.valueOf(code);
    }
}
