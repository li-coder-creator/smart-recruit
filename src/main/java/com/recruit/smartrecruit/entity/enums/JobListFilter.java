package com.recruit.smartrecruit.entity.enums;

import lombok.Getter;

@Getter
public enum JobListFilter {
    ALL("全部", null),
    ACTIVE("招聘中", JobStatus.ACTIVE),
    PAUSED("暂停招聘", JobStatus.PAUSED),
    ENDED("已结束", JobStatus.ENDED),
    DRAFT("草稿", JobStatus.DRAFT);

    private final String description;
    private final JobStatus status;

    JobListFilter(String description, JobStatus status) {
        this.description = description;
        this.status = status;
    }
}
