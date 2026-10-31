package com.recruit.smartrecruit.entity;

import com.recruit.smartrecruit.entity.enums.ApplicationActorType;
import com.recruit.smartrecruit.entity.enums.ApplicationEventType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 投递业务事件实体。
 * 只追加的真实业务事件；不直接作为公开接口响应。
 */
@Data
@NoArgsConstructor
public class ApplicationEvent {
    /**
     * 事件ID
     */
    private Long id;
    /**
     * 关联投递ID
     */
    private Long applicationId;
    /**
     * 事件类型（投递/状态变更/面试安排等）
     */
    private ApplicationEventType eventType;
    /**
     * 变更前投递状态
     */
    private Integer fromStatus;
    /**
     * 变更后投递状态
     */
    private Integer toStatus;
    /**
     * 关联面试ID，非面试类事件为空
     */
    private Long interviewId;
    /**
     * 操作者用户ID，仅服务端记录，不对外暴露
     */
    private Long operatorUserId;
    /**
     * 操作者类型（求职者/企业/系统）
     */
    private ApplicationActorType actorType;
    /**
     * 事件发生时间
     */
    private LocalDateTime occurredAt;
    /**
     * 内部明细；阶段三状态事件为空，后续排期事件仅保存契约白名单。
     */
    private String detailJson;
}
