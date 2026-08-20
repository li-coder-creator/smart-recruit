package com.recruit.smartrecruit.service.impl;

import com.recruit.smartrecruit.dto.ApplicationApplyDTO;
import com.recruit.smartrecruit.dto.ApplicationStatusUpdateDTO;
import com.recruit.smartrecruit.entity.*;
import com.recruit.smartrecruit.entity.enums.ApplicationStatus;
import com.recruit.smartrecruit.entity.enums.UserRole;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.*;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.service.ApplicationService;
import com.recruit.smartrecruit.utils.JobStatus;
import com.recruit.smartrecruit.vo.ApplicationVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ApplicationServiceImpl implements ApplicationService {
   //注入
    private final ApplicationMapper applicationMapper;
    private final JobMapper jobMapper;
    private final ResumeMapper resumeMapper;
    private final PermissionService permissionService;
    private final UserMapper userMapper;

    public ApplicationServiceImpl(ApplicationMapper applicationMapper, JobMapper jobMapper, ResumeMapper resumeMapper, PermissionService permissionService, UserMapper userMapper) {
        this.applicationMapper = applicationMapper;
        this.jobMapper = jobMapper;
        this.resumeMapper = resumeMapper;
        this.permissionService = permissionService;
        this.userMapper = userMapper;
    }

    @Override
    public void apply(ApplicationApplyDTO dto, Long userId) {
        permissionService.requireJobSeeker(userId);
        // 1. 查询岗位
        Job job = jobMapper.findById(dto.getJobId());
        // 2. 判断岗位是否存在
        if (job == null) {
            throw new BusinessException("岗位不存在");
        }
        // 3. 判断岗位是否正在招聘
        if (job.getStatus() == JobStatus.PAUSED.getCode()) {
            throw new BusinessException("岗位已暂停招聘，暂不可投递");
        }
        // 4. 查询简历
        Resume resume = resumeMapper.findById(dto.getResumeId());
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
            throw new BusinessException("请勿重复投递同一岗位");
        }
        // 8. 创建投递记录
        Application newApplication = new Application();
        newApplication.setUserId(userId);
        newApplication.setJobId(dto.getJobId());
        newApplication.setResumeId(dto.getResumeId());
        newApplication.setStatus(
                ApplicationStatus.PENDING.getCode()
        );
        // 9. 保存投递记录
        applicationMapper.apply(newApplication);
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
    public ApplicationVO findApplicationDetail(Long id, Long userId) {

        ApplicationVO application =
                applicationMapper.findApplicationDetail(id);

        if (application == null) {
            throw new BusinessException("该投递不存在");
        }
        User user = userMapper.findById(userId);

        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 求职者：只能查看自己的投递
        if (user.getRole() == UserRole.JOB_SEEKER) {

            permissionService.requireJobSeeker(userId);

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
        // 状态枚举翻译
        application.setStatusText(
                ApplicationStatus
                        .fromCode(application.getStatus())
                        .getDescription()
        );
        return application;
    }

    @Override
    public void updateStatus(Long id, Long userId, ApplicationStatusUpdateDTO dto) {
        Company company=permissionService.requireApprovedCompany(userId);
        //判断投递是否存在
        ApplicationVO application=applicationMapper.findApplicationDetail(id);
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
        ApplicationStatus applicationStatus =
                ApplicationStatus.fromCode(dto.getStatus());

        applicationMapper.updateStatus(
                id,
                applicationStatus.getCode()
        );
    }

}
