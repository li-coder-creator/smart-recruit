package com.recruit.smartrecruit.service;

import com.recruit.smartrecruit.constant.ApplicationConstants;
import com.recruit.smartrecruit.entity.ApplicationInterview;
import com.recruit.smartrecruit.entity.enums.ApplicationActorType;
import com.recruit.smartrecruit.entity.enums.ApplicationStatus;
import com.recruit.smartrecruit.entity.enums.InterviewCancelSource;
import com.recruit.smartrecruit.entity.enums.InterviewStatus;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.ApplicationInterviewMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 投递状态变更的面试生命周期处理器：投递进入终态时联动取消其未结束的面试排期。
 *
 * <p>事务约束：类级 {@link Propagation#MANDATORY}，调用方必须已开启事务并
 * 已锁定、更新父投递行；状态事件与有效排期失效必须同事务提交。
 *
 * <p>与 ApplicationEventRecorder 同属内部领域协作组件，仅被其他 Service 调用、
 * 唯一实现，故不拆接口。
 */
@Service
//只加入调用者的事务，绝不自己开事务
@Transactional(propagation = Propagation.MANDATORY)
public class ApplicationInterviewLifecycle {
    private final ApplicationInterviewMapper mapper;
    private final ApplicationEventRecorder eventRecorder;

    public ApplicationInterviewLifecycle(ApplicationInterviewMapper mapper, ApplicationEventRecorder eventRecorder) {
        this.mapper = mapper;
        this.eventRecorder = eventRecorder;
    }

    /**
     * 投递状态流转后的联动处理入口。
     *
     * <p>核心规则：
     * <ul>
     * <li>只有进入终态（HIRED / REJECTED）才需要取消在途排期，其余流转仅追加状态事件；
     * <li>锁顺序固定为「父投递 → 排期 → 事件」，与调用方保持一致，避免并发下死锁；
     * <li>每条排期以 version 做 CAS 更新，影响行数不为 1 视为并发冲突，
     * 抛异常回滚整个事务（含已追加的状态事件）；
     * <li>取消成功后同步内存行的 version，再追加 SYSTEM 角色的取消事件，
     * 保证返回给上文的实体与库内状态一致。
     * </ul>
     *
     * @param applicationId 投递 ID（调用方已对其加行锁）
     * @param from          流转前状态
     * @param to            流转后状态
     * @param userId        操作人用户 ID
     * @param occurredAt    变更发生时间（服务器统一时间）
     */
    public void onStatusChanged(Long applicationId, ApplicationStatus from, ApplicationStatus to,
            Long userId, LocalDateTime occurredAt) {
        // 只有进入终态才需要取消在途排期，其余流转仅追加状态事件。
        boolean terminal = to == ApplicationStatus.HIRED || to == ApplicationStatus.REJECTED;
        // 在追加任何事件前取得所有排期行锁，顺序始终为父投递、排期、事件。
        List<ApplicationInterview> open = terminal
                ? mapper.findOpenForUpdate(applicationId, InterviewStatus.SCHEDULED, occurredAt) : List.of();
        // 先记状态事件，再逐条失效排期；任一步失败整体回滚。
        eventRecorder.recordStatusChanged(applicationId, from, to, userId, occurredAt);
        for (ApplicationInterview row : open) {
            Long expectedVersion = row.getVersion();
            row.setStatus(InterviewStatus.CANCELLED);
            row.setCancelReason(ApplicationConstants.APPLICATION_ENDED_CANCEL_REASON);
            row.setCancelSource(InterviewCancelSource.APPLICATION_STATUS);
            row.setCancelledAt(occurredAt);
            row.setUpdatedBy(userId);
            row.setUpdateTime(occurredAt);
            // CAS：仅当仍为 SCHEDULED 且 version 未变时才能取消成功。
            if (mapper.cancel(row, expectedVersion, InterviewStatus.SCHEDULED, occurredAt) != 1) {
                throw new BusinessException(ApplicationConstants.INTERVIEW_CONFLICT_MESSAGE);
            }
            // 同步内存版本号，使其与 CAS 成功后的库内行一致，供事件快照使用。
            row.setVersion(expectedVersion + 1);
            eventRecorder.recordInterviewCancelled(row, ApplicationActorType.SYSTEM, userId, occurredAt);
        }
    }
}
