package com.recruit.smartrecruit.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 单岗位招聘进展统计。
 * 草稿岗位返回数量为 0、转化率为 null，前端依据 jobStatus 展示“尚未发布”；
 * 暂停和结束岗位正常返回历史统计。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobRecruitmentProgressVO {

    /**
     * 岗位 ID
     */
    private Long jobId;

    /**
     * 当前岗位状态
     */
    private Integer jobStatus;

    /**
     * 累计投递数
     */
    private Long applicationCount;

    /**
     * 待处理投递数
     */
    private Long pendingApplicationCount;

    /**
     * 当前面试中的投递数
     */
    private Long currentInterviewApplicationCount;

    /**
     * 曾实际进入面试的投递数；存在无法核实面试历史的旧投递时返回 null。
     */
    private Long interviewApplicationCount;

    /**
     * 累计面试投递数 / 累计投递数 * 100，保留两位小数。
     * 无投递或累计面试历史无法核实时返回 null。
     */
    private BigDecimal interviewConversionRate;
}
