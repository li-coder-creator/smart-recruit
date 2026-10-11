package com.recruit.smartrecruit.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 企业招聘总览。
 * “招聘中岗位数”直接取 jobCounts.active。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyJobOverviewVO {

    /**
     * 各状态岗位数量
     */
    private JobStatusCountVO jobCounts;

    /**
     * 本企业招聘中、暂停、已结束岗位的累计投递数，不受岗位列表筛选和分页影响。
     */
    private Long applicationCount;

    /**
     * 当前待处理投递数
     */
    private Long pendingApplicationCount;

    /**
     * 当前面试中的投递数
     */
    private Long currentInterviewApplicationCount;

    /**
     * 曾实际进入面试的投递数；统计范围内存在无法核实面试历史的旧投递时返回 null。
     */
    private Long interviewApplicationCount;

    /**
     * 累计面试投递数 / 累计投递数 * 100，保留两位小数。
     * 无投递或累计面试历史无法核实时返回 null。
     */
    private BigDecimal interviewConversionRate;
}
