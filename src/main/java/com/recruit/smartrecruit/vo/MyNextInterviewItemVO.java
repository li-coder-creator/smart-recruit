package com.recruit.smartrecruit.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 下一场面试列表项。
 * 继承面试安排展示字段，额外补充岗位与企业信息便于求职者识别。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MyNextInterviewItemVO extends ApplicationInterviewVO {
    /**
     * 关联岗位ID
     */
    private Long jobId;
    /**
     * 岗位名称
     */
    private String jobTitle;
    /**
     * 关联企业ID
     */
    private Long companyId;
    /**
     * 企业名称
     */
    private String companyName;
    /**
     * 企业Logo，无 Logo 时为空，由前端提供中性占位
     */
    private String companyLogo;
}
