package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.enums.InterviewMode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 面试变更快照。
 * 面试变更的白名单快照，不复制会议链接、地址或内部账号信息。
 */
@Data
@NoArgsConstructor
public class InterviewChangeVO {
    /**
     * 变更前开始时间，首次安排时为空
     */
    private LocalDateTime previousStartTime;
    /**
     * 变更前结束时间，首次安排时为空
     */
    private LocalDateTime previousEndTime;
    /**
     * 变更后开始时间
     */
    private LocalDateTime startTime;
    /**
     * 变更后结束时间
     */
    private LocalDateTime endTime;
    /**
     * 变更前面试方式，首次安排时为空
     */
    private InterviewMode previousMode;
    /**
     * 变更后面试方式
     */
    private InterviewMode mode;
    /**
     * 取消原因，仅取消事件存在
     */
    private String cancelReason;
}
