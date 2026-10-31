package com.recruit.smartrecruit.dto;

import com.recruit.smartrecruit.entity.enums.ApplicationActorType;
import com.recruit.smartrecruit.entity.enums.ApplicationEventType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投递事件查询行。
 * Mapper 内部投影；原始 JSON 不直接作为接口响应。
 */
@Data
public class ApplicationEventQueryRow {
    /**
     * 事件ID
     */
    private Long id;
    /**
     * 事件类型（如投递、状态变更、面试安排等）
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
     * 操作者类型（求职者/企业/管理员/系统）
     */
    private ApplicationActorType actorType;
    /**
     * 事件发生时间
     */
    private LocalDateTime occurredAt;
    /**
     * 关联面试ID，非面试类事件为空
     */
    private Long interviewId;
    /**
     * 事件明细JSON，仅用于服务端解析后组装响应
     */
    private String detailJson;
}
