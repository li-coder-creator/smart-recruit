package com.recruit.smartrecruit.service.impl;

import com.recruit.smartrecruit.constant.ApplicationConstants;
import com.recruit.smartrecruit.dto.ApplicationApplyDTO;
import com.recruit.smartrecruit.dto.ApplicationStatusUpdateDTO;
import com.recruit.smartrecruit.entity.*;
import com.recruit.smartrecruit.entity.enums.ApplicationStatus;
import com.recruit.smartrecruit.entity.enums.UserRole;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.*;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.service.ApplicationService;
import com.recruit.smartrecruit.service.ApplicationEventRecorder;
import com.recruit.smartrecruit.service.ApplicationInterviewLifecycle;
import com.recruit.smartrecruit.entity.enums.JobStatus;
import com.recruit.smartrecruit.vo.ApplicationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class ApplicationServiceImpl implements ApplicationService {
   //注入
    private final ApplicationMapper applicationMapper;
    private final JobMapper jobMapper;
    private final ResumeMapper resumeMapper;
    private final PermissionService permissionService;
    private final UserMapper userMapper;
    private final ApplicationEventRecorder eventRecorder;
    private final ApplicationInterviewLifecycle interviewLifecycle;
    private final Clock clock;

    public ApplicationServiceImpl(ApplicationMapper applicationMapper, JobMapper jobMapper, ResumeMapper resumeMapper,
            PermissionService permissionService, UserMapper userMapper, ApplicationEventRecorder eventRecorder,
            @Qualifier("applicationClock") Clock clock, ApplicationInterviewLifecycle interviewLifecycle) {
        this.applicationMapper = applicationMapper;
        this.jobMapper = jobMapper;
        this.resumeMapper = resumeMapper;
        this.permissionService = permissionService;
        this.userMapper = userMapper;
        this.eventRecorder = eventRecorder;
        this.clock = clock;
        this.interviewLifecycle = interviewLifecycle;
    }

    @Override
    @Transactional
    public void apply(ApplicationApplyDTO dto, Long userId) {
        permissionService.requireJobSeeker(userId);
        // 1. 查询岗位
        Job job = jobMapper.findById(dto.getJobId());
        // 2. 判断岗位是否存在
        if (job == null) {
            throw new BusinessException("岗位不存在");
        }
        // 3. 判断岗位是否正在招聘：仅招聘中的岗位可投递
        if (job.getStatus() != JobStatus.ACTIVE.getCode()) {
            throw new BusinessException("岗位当前不可投递");
        }
        permissionService.requireApprovedJobCompany(job.getCompanyId());
        // 4. 查询简历
        Resumebasic resume = resumeMapper.findById(dto.getResumeId());
        // 5. 判断简历是否存在
        if (resume == null) {
            throw new BusinessException("简历不存在");
        }
        // 6. 判断简历是否属于当前用户
        if (!resume.getUserId().equals(userId)) {
            throw new BusinessException("无权使用该简历");
        }
        // 7. 判断是否重复投递
        Application application = applicationMapper.findByUserIdAndJobId(userId, dto.getJobId());
        if (application != null) {
            throw new BusinessException(ApplicationConstants.DUPLICATE_APPLICATION_MESSAGE);
        }
        // 8. 创建投递记录
        Application newApplication = new Application();
        newApplication.setUserId(userId);
        newApplication.setJobId(dto.getJobId());
        newApplication.setResumeId(dto.getResumeId());
        newApplication.setStatus(
                ApplicationStatus.PENDING.getCode()
        );
        LocalDateTime occurredAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        // 原主表 DATETIME 精度为秒；事件保留毫秒，使用同一次服务器时间。
        newApplication.setCreateTime(occurredAt.truncatedTo(ChronoUnit.SECONDS));
        newApplication.setUpdateTime(newApplication.getCreateTime());
        newApplication.setHistoryRecordedFromStart(true);
        // 9. 唯一约束兜底并发重复投递；先查再插入本身不能保证唯一。
        int affectedRows;
        try {
            affectedRows = applicationMapper.apply(newApplication);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ApplicationConstants.DUPLICATE_APPLICATION_MESSAGE);
        }
        if (affectedRows != 1 || newApplication.getId() == null) {
            throw new BusinessException("投递保存失败，请重试");
        }
        // 10. 主键回填后追加初始事件；任一步失败，投递、标记和事件一起回滚。
        eventRecorder.recordApplied(newApplication.getId(), userId, occurredAt);
    }
    @Override
    public List<ApplicationVO> findCompanyApplication(Long userId) {
        Company company=permissionService.requireApprovedCompany(userId);
        List<ApplicationVO> applications=applicationMapper.findCompanyApplication(company.getId());
        //遍历每条申请记录，做一个**枚举翻译**：
        applications.forEach(application ->
                application.setStatusText(
                        ApplicationStatus
                                .fromCode(application.getStatus())
                                .getDescription()
                )
        );
        //展示投递
        return applications;
    }
    @Override
    public List<ApplicationVO> findJobseekerApplication(Long userId) {
        permissionService.requireJobSeeker(userId);
        List<ApplicationVO> applications=applicationMapper.findJobseekerApplication(userId);
        //遍历每条申请记录，做一个**枚举翻译**：
        applications.forEach(application ->
                application.setStatusText(
                        ApplicationStatus
                                .fromCode(application.getStatus())
                                .getDescription()
                )
        );

        //展示投递
        return applications;
    }
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ApplicationVO findApplicationDetail(Long id, Long userId) {

        // 先依据原始投递判断存在性与归属，避免展示资料缺失影响权限判断。
        Application application = applicationMapper.findById(id);

        if (application == null) {
            throw new BusinessException("该投递不存在");
        }
        User user = userMapper.findById(userId);

        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 求职者：只能查看自己的投递
        if (user.getRole() == UserRole.JOB_SEEKER) {

            if (!application.getUserId().equals(userId)) {
                throw new BusinessException("无权访问该投递");
            }

        }
        // 企业：只能查看自己企业收到的投递
        else if (user.getRole() == UserRole.COMPANY) {

            Company company =
                    permissionService.requireApprovedCompany(userId);

            Job job = jobMapper.findById(application.getJobId());

            if (job == null) {
                throw new BusinessException("该岗位不存在");
            }

            if (!job.getCompanyId().equals(company.getId())) {
                throw new BusinessException("无权访问该投递");
            }

        }
        else {

            throw new BusinessException("无权访问该投递");

        }
        // 权限通过后读取展示资料，简历缺失时由 VO 标记不可用。
        ApplicationVO detail = applicationMapper.findApplicationDetail(id);
        if (detail == null) {
            throw new BusinessException("投递详情读取失败，请刷新后重试");
        }
        // 状态枚举翻译
        detail.setStatusText(
                ApplicationStatus
                        .fromCode(application.getStatus())
                        .getDescription()
        );
        return detail;
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Long userId, ApplicationStatusUpdateDTO dto) {
        Company company=permissionService.requireApprovedCompany(userId);
        //判断投递是否存在
        Application application=applicationMapper.findById(id);
        if(application==null){
            throw  new BusinessException("该投递不存在");
        }
        Job job=jobMapper.findById(application.getJobId());
        //判断岗位是否存在
        if(job==null){
            throw  new BusinessException("该岗位不存在");
        }
        //判断查看的岗位是否属于该公司
        if(!job.getCompanyId().equals(company.getId())){
            throw  new BusinessException("无权修改该投递");
        }
        // 归属确认后锁定父行。保留原有并发冲突语义，不悄悄按另一个新状态重试。
        Application locked = applicationMapper.findByIdForUpdate(id, application.getJobId());
        if (locked == null || !Objects.equals(locked.getStatus(), application.getStatus())) {
            throw new BusinessException(ApplicationConstants.STATUS_CHANGED_MESSAGE);
        }
        ApplicationStatus currentStatus = requireStoredStatus(locked);
        ApplicationStatus targetStatus;
        try {
            targetStatus = ApplicationStatus.fromCode(dto.getStatus());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("无效的目标投递状态");
        }
        if (currentStatus == targetStatus) {
            throw new BusinessException("投递已处于目标状态");
        }
        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new BusinessException("不能从" + currentStatus.getDescription()
                    + "转为" + targetStatus.getDescription());
        }
        LocalDateTime occurredAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        int affectedRows = applicationMapper.updateStatus(
                id,
                application.getJobId(),
                currentStatus,
                targetStatus,
                ApplicationStatus.INTERVIEW,
                occurredAt.truncatedTo(ChronoUnit.SECONDS));
        if (affectedRows != 1) {
            throw new BusinessException(ApplicationConstants.STATUS_CHANGED_MESSAGE);
        }
        // 记录真实起点和终点；跳级只追加一条，不补造被跳过的阶段。
        interviewLifecycle.onStatusChanged(id, currentStatus, targetStatus, userId, occurredAt);
    }

    private ApplicationStatus requireStoredStatus(Application application) {
        try {
            return ApplicationStatus.fromCode(application.getStatus());
        } catch (IllegalArgumentException exception) {
            log.error("投递状态数据异常，applicationId={} status={}", application.getId(), application.getStatus());
            throw new BusinessException(ApplicationConstants.INVALID_DATA_MESSAGE);
        }
    }
}
