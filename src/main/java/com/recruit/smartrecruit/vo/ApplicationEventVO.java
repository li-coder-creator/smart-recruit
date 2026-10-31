package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.enums.ApplicationActorType;
import com.recruit.smartrecruit.entity.enums.ApplicationEventType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 投递事件展示对象。
 * 投递真实事件的展示白名单，不暴露操作者账号 ID 或原始明细 JSON。
 */
@Data
@NoArgsConstructor
public class ApplicationEventVO {
    /**
     * 事件ID
     */
    private Long id;
    /**
     * 事件类型（投递/状态变更/面试安排等）
     */
    private ApplicationEventType eventType;
    /** APPLIED 为空；STATUS_CHANGED 为实际原状态；排期事件为空。 */
    private Integer fromStatus;
    /** APPLIED 为待处理；STATUS_CHANGED 为实际目标状态；排期事件为空。 */
    private Integer toStatus;
    /**
     * 操作者类型（求职者/企业/系统）
     */
    private ApplicationActorType actorType;
    /**
     * 事件发生时间
     */
    private LocalDateTime occurredAt;
    /** 由服务端使用事件类型与白名单内容生成的求职者可见说明。 */
    private String summary;
    /** 仅面试排期事件存在。 */
    private Long interviewId;
    /**
     * 面试变更快照，仅面试排期类事件存在
     */
    private InterviewChangeVO interviewChange;
}
