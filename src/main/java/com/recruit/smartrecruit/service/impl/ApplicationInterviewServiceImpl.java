package com.recruit.smartrecruit.service.impl;

import com.recruit.smartrecruit.constant.ApplicationConstants;
import com.recruit.smartrecruit.dto.ApplicationInterviewCancelDTO;
import com.recruit.smartrecruit.dto.ApplicationInterviewCreateDTO;
import com.recruit.smartrecruit.dto.ApplicationInterviewUpdateDTO;
import com.recruit.smartrecruit.entity.Application;
import com.recruit.smartrecruit.entity.ApplicationInterview;
import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.Job;
import com.recruit.smartrecruit.entity.enums.ApplicationActorType;
import com.recruit.smartrecruit.entity.enums.ApplicationStatus;
import com.recruit.smartrecruit.entity.enums.InterviewCancelSource;
import com.recruit.smartrecruit.entity.enums.InterviewMode;
import com.recruit.smartrecruit.entity.enums.InterviewStatus;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.ApplicationInterviewMapper;
import com.recruit.smartrecruit.mapper.ApplicationMapper;
import com.recruit.smartrecruit.mapper.JobMapper;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.service.ApplicationEventRecorder;
import com.recruit.smartrecruit.service.ApplicationInterviewService;
import com.recruit.smartrecruit.utils.ApplicationInterviewViews;
import com.recruit.smartrecruit.vo.ApplicationInterviewListVO;
import com.recruit.smartrecruit.vo.ApplicationInterviewVO;
import com.recruit.smartrecruit.vo.MyNextInterviewItemVO;
import com.recruit.smartrecruit.vo.MyNextInterviewVO;
import jakarta.validation.Validator;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * 面试安排服务实现。
 *
 * <p>统一约定：
 * <ul>
 * <li>时间基准：不直接取系统时间，而是注入 applicationClock 并截断到毫秒，
 * 保证同一请求内时间一致、可测试；
 * <li>并发控制：写操作先锁定父投递行，再以 version 乐观锁（CAS）更新排期，
 * 影响行数不为 1 即判定并发冲突；
 * <li>审计：所有写操作通过 ApplicationEventRecorder 追加事件，与业务变更同事务提交。
 * </ul>
 */
@Service
public class ApplicationInterviewServiceImpl implements ApplicationInterviewService {
    private final ApplicationMapper applicationMapper;
    private final ApplicationInterviewMapper mapper;
    private final JobMapper jobMapper;
    private final PermissionService permissionService;
    private final ApplicationEventRecorder eventRecorder;
    /** 用于对 DTO 执行 Bean Validation，校验失败转为业务异常。 */
    private final Validator validator;
    /** 统一服务器时钟，便于测试替换与时区管控。 */
    private final Clock clock;

    public ApplicationInterviewServiceImpl(ApplicationMapper applicationMapper, ApplicationInterviewMapper mapper,
            JobMapper jobMapper, PermissionService permissionService, ApplicationEventRecorder eventRecorder,
            Validator validator, @Qualifier("applicationClock") Clock clock) {
        this.applicationMapper = applicationMapper;
        this.mapper = mapper;
        this.jobMapper = jobMapper;
        this.permissionService = permissionService;
        this.eventRecorder = eventRecorder;
        this.validator = validator;
        this.clock = clock;
    }

    /**
     * 列出某条投递的全部面试排期（企业侧）。
     * 只读事务 + REPEATABLE_READ 约束数据库读取快照；
     * 本次取得同一个服务器时间计算各场 timingStatus，派生状态不落库。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ApplicationInterviewListVO list(Long applicationId, Long userId) {
        requireCompanyApplication(applicationId, userId, false);
        LocalDateTime now = now();
        List<ApplicationInterviewVO> items = mapper.list(applicationId);
        items.forEach(item -> ApplicationInterviewViews.fillTimingStatus(item, now));
        ApplicationInterviewListVO result = new ApplicationInterviewListVO();
        result.setApplicationId(applicationId);
        result.setServerTime(now);
        result.setTimeZone(ApplicationConstants.TIME_ZONE);
        result.setItems(items);
        return result;
    }

    /**
     * 创建面试排期（企业侧）。
     *
     * <p>流程：校验 DTO → 锁定父投递并确认属于本企业 → 投递必须处于 INTERVIEW →
     * 锁内检查无在途排期（唯一有效排期约束）→ 以最新时间二次校验内容 → 落库并追加事件。
     *
     * <p>注意两次取 now：第一次用于锁内判断在途排期是否已过期，
     * 第二次作为正式时间基准，避免锁等待期间跨越时间点导致误判。
     */
    @Override
    @Transactional
    public ApplicationInterviewVO create(Long applicationId, ApplicationInterviewCreateDTO dto, Long userId) {
        validate(dto);
        Application application = requireCompanyApplication(applicationId, userId, true);
        requireInterviewStatus(application);
        LocalDateTime now = now();
        if (!mapper.findOpenForUpdate(applicationId, InterviewStatus.SCHEDULED, now).isEmpty()) {
            throw new BusinessException("本条投递已有未结束的面试安排");
        }
        now = now();
        validateContent(dto, now);
        ApplicationInterview row = new ApplicationInterview();
        copyContent(dto, row);
        row.setApplicationId(applicationId);
        row.setStatus(InterviewStatus.SCHEDULED);
        row.setVersion(ApplicationConstants.INITIAL_INTERVIEW_VERSION);
        row.setCreatedBy(userId);
        row.setUpdatedBy(userId);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        if (mapper.insert(row) != 1 || row.getId() == null) {
            throw new BusinessException("面试安排保存失败，请重试");
        }
        eventRecorder.recordInterviewScheduled(row, userId, now);
        return ApplicationInterviewViews.toVO(row, now);
    }

    /**
     * 改约面试（企业侧）。
     *
     * <p>仅允许改约「未开始且未取消」的排期：状态为 SCHEDULED 且开始时间晚于当前时间。
     * 先整体复制一份 previous 快照用于记录改约前后对比，再覆盖新内容；
     * CAS 更新成功后同步内存 version，保证返回 VO 与库内一致。
     */
    @Override
    @Transactional
    public ApplicationInterviewVO reschedule(Long applicationId, Long interviewId,
            ApplicationInterviewUpdateDTO dto, Long userId) {
        validate(dto);
        Application application = requireCompanyApplication(applicationId, userId, true);
        requireInterviewStatus(application);
        ApplicationInterview row = requireInterview(applicationId, interviewId);
        LocalDateTime now = now();
        requireVersion(row, dto.getVersion());
        if (row.getStatus() != InterviewStatus.SCHEDULED || !row.getStartTime().isAfter(now)) {
            throw new BusinessException("仅未开始且未取消的面试可以改约");
        }
        validateContent(dto, now);
        ApplicationInterview previous = new ApplicationInterview();
        BeanUtils.copyProperties(row, previous);
        copyContent(dto, row);
        row.setUpdatedBy(userId);
        row.setUpdateTime(now);
        requireUpdated(mapper.reschedule(row, dto.getVersion(), InterviewStatus.SCHEDULED, now));
        row.setVersion(row.getVersion() + 1);
        eventRecorder.recordInterviewRescheduled(previous, row, userId, now);
        return ApplicationInterviewViews.toVO(row, now);
    }

    /**
     * 企业主动取消面试。
     *
     * <p>可取消条件比改约宽松一档：允许结束时间尚未过去（含进行中），
     * 但已取消/已结束的行会因状态或时间校验被拒绝，防重复取消。
     * 取消来源记为 COMPANY，区别于投递终态联动取消（SYSTEM）。
     */
    @Override
    @Transactional
    public void cancel(Long applicationId, Long interviewId, ApplicationInterviewCancelDTO dto, Long userId) {
        validate(dto);
        Application application = requireCompanyApplication(applicationId, userId, true);
        requireInterviewStatus(application);
        ApplicationInterview row = requireInterview(applicationId, interviewId);
        LocalDateTime now = now();
        requireVersion(row, dto.getVersion());
        if (row.getStatus() != InterviewStatus.SCHEDULED || !row.getEndTime().isAfter(now)) {
            throw new BusinessException("面试已取消或已结束，不能重复取消");
        }
        row.setStatus(InterviewStatus.CANCELLED);
        row.setCancelReason(dto.getReason());
        row.setCancelSource(InterviewCancelSource.COMPANY);
        row.setCancelledAt(now);
        row.setUpdatedBy(userId);
        row.setUpdateTime(now);
        requireUpdated(mapper.cancel(row, dto.getVersion(), InterviewStatus.SCHEDULED, now));
        row.setVersion(row.getVersion() + 1);
        eventRecorder.recordInterviewCancelled(row, ApplicationActorType.COMPANY, userId, now);
    }

    /**
     * 求职者查询最近一场待参加面试（首页卡片用）。
     * 条件：投递处于 INTERVIEW、排期为 SCHEDULED 且未过期，取开始时间最早的一场；
     * 无结果时 interview 为 null，前端降级展示空态。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MyNextInterviewVO next(Long userId) {
        permissionService.requireJobSeeker(userId);
        LocalDateTime now = now();
        MyNextInterviewItemVO interview = mapper.findNext(userId, ApplicationStatus.INTERVIEW,
                InterviewStatus.SCHEDULED, now);
        if (interview != null) {
            ApplicationInterviewViews.fillTimingStatus(interview, now);
        }
        MyNextInterviewVO result = new MyNextInterviewVO();
        result.setServerTime(now);
        result.setTimeZone(ApplicationConstants.TIME_ZONE);
        result.setInterview(interview);
        return result;
    }

    /**
     * 校验投递存在、属于当前审核通过的企业（经 Job→Company 链路反查）。
     *
     * <p>越权访问统一返回 NOT_FOUND，不区分「不存在」与「无权」，避免泄露资源存在性。
     * lock=true 时额外对投递行加写锁，并确认加锁前后状态未被并发修改，
     * 已变则提示刷新重试，保证后续判断基于锁定后的最新状态。
     */
    private Application requireCompanyApplication(Long id, Long userId, boolean lock) {
        Company company = permissionService.requireApprovedCompany(userId);
        Application application = id == null || id <= 0 ? null : applicationMapper.findById(id);
        if (application == null) {
            throw new BusinessException(ApplicationConstants.NOT_FOUND_MESSAGE);
        }
        Job job = jobMapper.findById(application.getJobId());
        if (job == null || !company.getId().equals(job.getCompanyId())) {
            throw new BusinessException(ApplicationConstants.NOT_FOUND_MESSAGE);
        }
        if (lock) {
            Application locked = applicationMapper.findByIdForUpdate(id, application.getJobId());
            if (locked == null || !Objects.equals(locked.getStatus(), application.getStatus())) {
                throw new BusinessException(ApplicationConstants.STATUS_CHANGED_MESSAGE);
            }
            return locked;
        }
        return application;
    }

    /** 按投递 + 排期双条件加行锁查询排期，防跨投递越权操作他人面试。 */
    private ApplicationInterview requireInterview(Long applicationId, Long interviewId) {
        ApplicationInterview row = interviewId == null || interviewId <= 0 ? null
                : mapper.findForUpdate(applicationId, interviewId);
        if (row == null) {
            throw new BusinessException(ApplicationConstants.INTERVIEW_NOT_FOUND_MESSAGE);
        }
        return row;
    }

    /** 投递必须处于 INTERVIEW 状态才允许操作面试排期。 */
    private void requireInterviewStatus(Application application) {
        if (!ApplicationStatus.INTERVIEW.getCode().equals(application.getStatus())) {
            throw new BusinessException("仅面试中的投递可操作面试安排");
        }
    }

    /** 乐观锁前置校验：前端回传的 version 必须与库内一致，不一致提示并发冲突。 */
    private void requireVersion(ApplicationInterview row, Long expected) {
        if (!Objects.equals(row.getVersion(), expected)) {
            throw new BusinessException(ApplicationConstants.INTERVIEW_CONFLICT_MESSAGE);
        }
    }

    /** CAS 更新后置校验：影响行数不为 1 说明锁外被并发改写。 */
    private void requireUpdated(int affectedRows) {
        if (affectedRows != 1) {
            throw new BusinessException(ApplicationConstants.INTERVIEW_CONFLICT_MESSAGE);
        }
    }

    /** 执行 Bean Validation，取第一条违反项的消息转为业务异常。 */
    private void validate(Object dto) {
        if (dto == null) {
            throw new BusinessException("面试参数不能为空");
        }
        validator.validate(dto).stream().findFirst().ifPresent(violation -> {
            throw new BusinessException(violation.getMessage());
        });
    }

    /**
     * 排期内容业务校验（DTO 注解覆盖不到的规则）：
     * 时间先后关系 + 线上/线下两种方式字段互斥；
     * 会议链接必须是带主机的 http(s) 地址，防注入 javascript: 等危险协议。
     */
    private void validateContent(ApplicationInterviewCreateDTO dto, LocalDateTime now) {
        if (!dto.getStartTime().isAfter(now)) {
            throw new BusinessException("面试开始时间必须晚于当前时间");
        }
        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new BusinessException("面试结束时间必须晚于开始时间");
        }
        if (dto.getMode() == InterviewMode.ONLINE) {
            if (dto.getPlatform() == null || dto.getMeetingUrl() == null) {
                throw new BusinessException("线上面试必须填写平台和会议链接");
            }
            if (dto.getAddress() != null) {
                throw new BusinessException("线上面试不能同时填写线下地址");
            }
            try {
                URI uri = new URI(dto.getMeetingUrl());
                if ((!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null) {
                    throw new BusinessException("会议链接必须是有效的http或https地址");
                }
            } catch (URISyntaxException exception) {
                throw new BusinessException("会议链接必须是有效的http或https地址");
            }
        } else {
            if (dto.getAddress() == null) {
                throw new BusinessException("线下面试必须填写地址");
            }
            if (dto.getPlatform() != null || dto.getMeetingUrl() != null) {
                throw new BusinessException("线下面试不能同时填写线上平台或链接");
            }
        }
    }

    /** 只拷贝可编辑的排期内容字段；状态、版本、操作人等由调用方单独维护。 */
    private void copyContent(ApplicationInterviewCreateDTO dto, ApplicationInterview row) {
        row.setStartTime(dto.getStartTime());
        row.setEndTime(dto.getEndTime());
        row.setMode(dto.getMode());
        row.setPlatform(dto.getPlatform());
        row.setMeetingUrl(dto.getMeetingUrl());
        row.setAddress(dto.getAddress());
        row.setCandidateNote(dto.getCandidateNote());
    }

    /** 统一时间源：截断到毫秒，与 MySQL datetime(3) 精度对齐，避免内存值与库内值不一致。 */
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }
}
