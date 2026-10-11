package com.recruit.smartrecruit.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 企业岗位列表：复用岗位展示字段，额外提供本岗位的招聘统计。 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CompanyJobListVO extends JobListVO {
    /** 全部状态的投递记录数。 */
    private Long applicationCount = 0L;
    /** 当前待处理投递数，即列表中的“新”。 */
    private Long pendingApplicationCount = 0L;
    /** 当前面试中的投递数，不是累计面试数。 */
    private Long currentInterviewApplicationCount = 0L;
}
