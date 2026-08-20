package com.recruit.smartrecruit.utils;

import lombok.Getter;

@Getter
public enum JobStatus {
    ACTIVE(0, "招聘中"),
    PAUSED(1, "暂停招聘");

    private final int code;
    private final String description;

    JobStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static JobStatus fromCode(int code) {
        for (JobStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的岗位状态：" + code);
    }
}
