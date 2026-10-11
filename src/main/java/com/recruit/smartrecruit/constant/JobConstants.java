package com.recruit.smartrecruit.constant;

import com.recruit.smartrecruit.entity.enums.JobSalaryUnit;

import java.math.BigDecimal;

public final class JobConstants {
    //默认招聘人数
    public static final int DEFAULT_HEADCOUNT = 1;
    //默认薪资单位：按月
    public static final JobSalaryUnit DEFAULT_SALARY_UNIT = JobSalaryUnit.MONTH;
    //默认不薪资面议
    public static final boolean DEFAULT_NEGOTIABLE = false;
    // 岗位详情最近投递的默认展示条数。
    public static final int DEFAULT_RECENT_APPLICATION_LIMIT = 5;
    // 岗位业务时间线的默认展示条数。
    public static final int DEFAULT_JOB_ACTIVITY_LIMIT = 5;
    public static final long JOB_DETAIL_CACHE_SECOND_EVICTION_DELAY_MILLIS = 100L;
    // 面试转化率以百分数返回，保留两位小数。
    public static final BigDecimal INTERVIEW_CONVERSION_PERCENT_MULTIPLIER = BigDecimal.valueOf(100);
    public static final int INTERVIEW_CONVERSION_RATE_SCALE = 2;

    private JobConstants() {
    }
}
