package com.recruit.smartrecruit.entity.enums;

import lombok.Getter;

@Getter
public enum ResumeStatus {

    DRAFT(0, "草稿"),
    PUBLISHED(1, "可投递"),
    INCOMPLETE(2, "待完善");

    private final Integer code;
    private final String description;

    ResumeStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }
}