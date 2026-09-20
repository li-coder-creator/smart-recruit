package com.recruit.smartrecruit.entity.enums;

import lombok.Getter;

@Getter
public enum ResumeListFilter {

    ALL("全部"),
    APPLYING("投递中"),
    FAVORITE("收藏"),
    INCOMPLETE("待完善");

    private final String description;

    ResumeListFilter(String description) {
        this.description = description;
    }
}