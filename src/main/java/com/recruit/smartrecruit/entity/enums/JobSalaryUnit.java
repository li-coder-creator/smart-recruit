package com.recruit.smartrecruit.entity.enums;

import lombok.Getter;
@Getter
public enum JobSalaryUnit {
    DAY("日"), MONTH("月"), YEAR("年");
    private String desc;
    JobSalaryUnit(String desc) {
        this.desc = desc;
    }
    public String getDesc() {
        return desc;
    }
}
