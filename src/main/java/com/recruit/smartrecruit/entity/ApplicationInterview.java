package com.recruit.smartrecruit.entity;

import com.recruit.smartrecruit.entity.enums.InterviewCancelSource;
import com.recruit.smartrecruit.entity.enums.InterviewMode;
import com.recruit.smartrecruit.entity.enums.InterviewStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 投递面试安排实体。
 * 记录某一投递对应的面试时间、方式、地点/链接及取消信息。
 */
@Data
@NoArgsConstructor
public class ApplicationInterview {
    /**
     * 面试记录ID
     */
    private Long id;
    /**
     * 关联投递ID
     */
    private Long applicationId;
    /**
     * 面试开始时间
     */
    private LocalDateTime startTime;
    /**
     * 面试结束时间
     */
    private LocalDateTime endTime;
    /**
     * 面试方式（线上/线下）
     */
    private InterviewMode mode;
    /**
     * 面试平台名称，线上面试使用
     */
    private String platform;
    /**
     * 会议链接，线上面试使用
     */
    private String meetingUrl;
    /**
     * 面试地址，线下面试使用
     */
    private String address;
    /**
     * 给求职者的补充说明
     */
    private String candidateNote;
    /**
     * 面试存储状态（已安排/已取消）
     */
    private InterviewStatus status;
    /**
     * 并发版本号，用于乐观锁校验
     */
    private Long version;
    /**
     * 取消原因
     */
    private String cancelReason;
    /**
     * 取消来源（企业主动/投递状态联动），服务端推导
     */
    private InterviewCancelSource cancelSource;
    /**
     * 取消时间
     */
    private LocalDateTime cancelledAt;
    /**
     * 创建人用户ID
     */
    private Long createdBy;
    /**
     * 最后修改人用户ID
     */
    private Long updatedBy;
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
