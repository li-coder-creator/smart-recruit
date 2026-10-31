package com.recruit.smartrecruit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.recruit.smartrecruit.constant.ApplicationConstants;
import com.recruit.smartrecruit.entity.ApplicationEvent;
import com.recruit.smartrecruit.entity.ApplicationInterview;
import com.recruit.smartrecruit.entity.enums.ApplicationActorType;
import com.recruit.smartrecruit.entity.enums.ApplicationEventType;
import com.recruit.smartrecruit.entity.enums.ApplicationStatus;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.ApplicationEventMapper;
import com.recruit.smartrecruit.vo.InterviewChangeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 投递事件记录器：追加投递/面试的审计流水（application_event）。
 *
 * <p>事务约束：类级 {@link Propagation#MANDATORY} 表示调用方必须已开启事务，
 * 本类只加入调用者事务、绝不独立提交，保证事件与业务变更同事务原子生效；
 * 在无事务上下文中调用会直接抛出 IllegalTransactionStateException，而不是
 * 静默开启独立事务导致审计数据与业务变更脱钩。
 *
 * <p>本类是内部领域协作组件，仅被其他 Service 调用、不对外暴露，
 * 唯一实现且无需替换，因此不遵循 service 接口 + impl 的分层模式。
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ApplicationEventRecorder {
    private final ApplicationEventMapper mapper;
    /** 用于把面试变更快照序列化为 detailJson。 */
    private final ObjectMapper objectMapper;

    public ApplicationEventRecorder(ApplicationEventMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 记录求职者投递成功事件：无前置状态，toStatus 固定为 PENDING。
     *
     * @param applicationId  投递 ID
     * @param operatorUserId 操作人（求职者）用户 ID
     * @param occurredAt     事件发生时间（由调用方以服务器统一时间传入）
     */
    public void recordApplied(Long applicationId, Long operatorUserId, LocalDateTime occurredAt) {
        ApplicationEvent event = newEvent(applicationId, operatorUserId, occurredAt);
        event.setEventType(ApplicationEventType.APPLIED);
        event.setActorType(ApplicationActorType.JOB_SEEKER);
        event.setToStatus(ApplicationStatus.PENDING.getCode());
        append(event);
    }

    /**
     * 记录企业侧投递状态流转事件。
     *
     * <p>追加事件前先校验 from → to 是合法的状态机流转，非法流转直接拒绝，
     * 防止审计流水出现凭空捏造的状态变化。
     */
    public void recordStatusChanged(Long applicationId, ApplicationStatus from, ApplicationStatus to,
            Long operatorUserId, LocalDateTime occurredAt) {
        if (from == null || !from.canTransitionTo(to)) {
            throw new BusinessException(ApplicationConstants.INVALID_DATA_MESSAGE);
        }
        ApplicationEvent event = newEvent(applicationId, operatorUserId, occurredAt);
        event.setEventType(ApplicationEventType.STATUS_CHANGED);
        event.setActorType(ApplicationActorType.COMPANY);
        event.setFromStatus(from.getCode());
        event.setToStatus(to.getCode());
        append(event);
    }

    /** 记录「企业安排面试」事件，detailJson 保存新排期的时间/方式快照。 */
    public void recordInterviewScheduled(ApplicationInterview row, Long userId, LocalDateTime occurredAt) {
        appendInterview(row, ApplicationEventType.INTERVIEW_SCHEDULED, ApplicationActorType.COMPANY,
                snapshot(row), userId, occurredAt);
    }

    /**
     * 记录「企业改约面试」事件。
     *
     * <p>previous 与 row 必须指向同一条排期的同一条投递，否则快照对比没有意义；
     * detailJson 在最新快照基础上补充改约前的开始/结束时间、面试方式，供前端展示变更对比。
     */
    public void recordInterviewRescheduled(ApplicationInterview previous, ApplicationInterview row,
            Long userId, LocalDateTime occurredAt) {
        if (!row.getId().equals(previous.getId())
                || !row.getApplicationId().equals(previous.getApplicationId())) {
            throw new BusinessException(ApplicationConstants.INVALID_DATA_MESSAGE);
        }
        InterviewChangeVO change = snapshot(row);
        change.setPreviousStartTime(previous.getStartTime());
        change.setPreviousEndTime(previous.getEndTime());
        change.setPreviousMode(previous.getMode());
        appendInterview(row, ApplicationEventType.INTERVIEW_RESCHEDULED, ApplicationActorType.COMPANY,
                change, userId, occurredAt);
    }

    /**
     * 记录「面试取消」事件。
     *
     * <p>actor 只允许 COMPANY（企业主动取消）或 SYSTEM（投递终态联动取消），
     * 语义由调用方保证，这里做入参防御；detailJson 额外携带取消原因。
     */
    public void recordInterviewCancelled(ApplicationInterview row, ApplicationActorType actor,
            Long userId, LocalDateTime occurredAt) {
        if (actor != ApplicationActorType.COMPANY && actor != ApplicationActorType.SYSTEM) {
            throw new BusinessException(ApplicationConstants.INVALID_DATA_MESSAGE);
        }
        InterviewChangeVO change = snapshot(row);
        change.setCancelReason(row.getCancelReason());
        appendInterview(row, ApplicationEventType.INTERVIEW_CANCELLED, actor, change, userId, occurredAt);
    }

    /** 从排期行提取当前时间/方式快照，作为事件 detailJson 的基础内容。 */
    private InterviewChangeVO snapshot(ApplicationInterview row) {
        InterviewChangeVO change = new InterviewChangeVO();
        change.setStartTime(row.getStartTime());
        change.setEndTime(row.getEndTime());
        change.setMode(row.getMode());
        return change;
    }

    /**
     * 追加面试类事件的公共流程：校验排期行已落库（有合法 ID）→ 组装事件 →
     * 序列化快照 → 插入。
     *
     * <p>序列化显式关闭时间戳格式（WRITE_DATES_AS_TIMESTAMPS），保证 detailJson
     * 中的 LocalDateTime 以 ISO 字符串存储，可被读取侧稳定反序列化；
     * 序列化失败视为数据异常，抛业务异常回滚调用者的整个事务。
     */
    private void appendInterview(ApplicationInterview row, ApplicationEventType type,
            ApplicationActorType actor, InterviewChangeVO change, Long userId, LocalDateTime occurredAt) {
        if (row.getId() == null || row.getId() <= 0) {
            throw new BusinessException(ApplicationConstants.INVALID_DATA_MESSAGE);
        }
        ApplicationEvent event = newEvent(row.getApplicationId(), userId, occurredAt);
        event.setEventType(type);
        event.setActorType(actor);
        event.setInterviewId(row.getId());
        try {
            event.setDetailJson(objectMapper.writerFor(InterviewChangeVO.class)
                    .without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).writeValueAsString(change));
        } catch (JsonProcessingException exception) {
            throw new BusinessException("面试事件保存失败，请重试");
        }
        append(event);
    }

    /** 构造事件基础字段，所有入参在此统一做防御性校验，杜绝半空事件入库。 */
    private ApplicationEvent newEvent(Long applicationId, Long operatorUserId, LocalDateTime occurredAt) {
        if (applicationId == null || applicationId <= 0 || operatorUserId == null
                || operatorUserId <= 0 || occurredAt == null) {
            throw new BusinessException(ApplicationConstants.INVALID_DATA_MESSAGE);
        }
        ApplicationEvent event = new ApplicationEvent();
        event.setApplicationId(applicationId);
        event.setOperatorUserId(operatorUserId);
        event.setOccurredAt(occurredAt);
        return event;
    }

    /**
     * 追加事件流水：插入必须恰好影响 1 行且回填自增主键，
     * 否则抛异常回滚调用者事务，保证业务变更与审计记录严格同生同死。
     */
    private void append(ApplicationEvent event) {
        int affectedRows = mapper.insert(event);
        if (affectedRows != 1 || event.getId() == null) {
            throw new BusinessException("投递事件保存失败，请重试");
        }
    }
}
