package com.recruit.smartrecruit.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 求职者本人全部历史投递的当前状态计数，不受列表筛选条件影响。
 * 由聚合查询或 Service 完整赋值，没有记录时各计数应为零。
 */
@Data
@NoArgsConstructor
public class MyApplicationOverviewVO {
    /**
     * 投递总数
     */
    private Long totalCount;
    /**
     * 待处理状态数量
     */
    private Long pendingCount;
    /**
     * 筛选中状态数量
     */
    private Long screeningCount;
    /** 当前处于面试状态的数量，不是累计曾进入面试的数量。 */
    private Long interviewCount;
    /**
     * 已录用状态数量
     */
    private Long hiredCount;
    /** 求职者界面显示为“未通过”。 */
    private Long rejectedCount;
}
