package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.enums.InterviewMode;
import com.recruit.smartrecruit.entity.enums.InterviewStatus;
import com.recruit.smartrecruit.entity.enums.InterviewTimingStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 仅供本人求职者和所属企业读取的面试安排。
 * 包含私有会议链接和地址，不能用于公开主页或普通投递列表。
 */
@Data
@NoArgsConstructor
public class ApplicationInterviewVO {
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
     * 会议链接，线上面试使用，仅本人可读
     */
    private String meetingUrl;
    /**
     * 面试地址，线下面试使用，仅本人可读
     */
    private String address;
    /** 求职者可见说明，不是企业内部评价。 */
    private String candidateNote;
    /** 数据库存储的安排状态。 */
    private InterviewStatus status;
    /** 由 Service 按本次统一时间派生，不代表实际出席或完成面试。 */
    private InterviewTimingStatus timingStatus;
    /**
     * 并发版本号，用于修改/取消时的乐观锁校验
     */
    private Long version;
    /**
     * 取消原因，仅已取消面试存在
     */
    private String cancelReason;
    /**
     * 取消时间
     */
    private LocalDateTime cancelledAt;
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
