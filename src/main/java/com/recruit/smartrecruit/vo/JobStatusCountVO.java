package com.recruit.smartrecruit.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 各状态岗位数量统计。
 * 总览中的“招聘中岗位数”直接取 active 字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobStatusCountVO {

    /**
     * 全部岗位数
     */
    private Long all;

    /**
     * 招聘中岗位数（status=0）
     */
    private Long active;

    /**
     * 暂停招聘岗位数（status=1）
     */
    private Long paused;

    /**
     * 草稿岗位数（status=3）
     */
    private Long draft;

    /**
     * 已结束岗位数（status=2）
     */
    private Long ended;
}
