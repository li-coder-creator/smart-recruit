package com.recruit.smartrecruit.service.impl;

import com.recruit.smartrecruit.dto.ApplicationApplyDTO;
import com.recruit.smartrecruit.dto.ApplicationStatusUpdateDTO;
import com.recruit.smartrecruit.entity.Application;
import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.Job;
import com.recruit.smartrecruit.entity.Resume;
import com.recruit.smartrecruit.entity.enums.CompanyStatus;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.ApplicationMapper;
import com.recruit.smartrecruit.mapper.CompanyMapper;
import com.recruit.smartrecruit.mapper.JobMapper;
import com.recruit.smartrecruit.mapper.ResumeMapper;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.service.ApplicationService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ApplicationServiceImpl implements ApplicationService {
   //注入
    private final ApplicationMapper applicationMapper;
    private final JobMapper jobMapper;
    private final ResumeMapper resumeMapper;
    private final PermissionService permissionService;

    public ApplicationServiceImpl(ApplicationMapper applicationMapper, JobMapper jobMapper, ResumeMapper resumeMapper, PermissionService permissionService) {
        this.applicationMapper = applicationMapper;
        this.jobMapper = jobMapper;
        this.resumeMapper = resumeMapper;
        this.permissionService = permissionService;
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
        if (job.getStatus() == 1) {
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
        // 0 = 待处理
        newApplication.setStatus(0);
        // 9. 保存投递记录
        applicationMapper.apply(newApplication);
    }
    @Override
    public List<Application> findCompanyApplication(Long userId) {
        Company company=permissionService.requireApprovedCompany(userId);
        List<Application> applications=applicationMapper.findCompanyApplication(company.getId());
        //展示投递
        return applications;
    }

    @Override
    public Application findCompanyApplicationById(Long id, Long userId) {
        Company company=permissionService.requireApprovedCompany(userId);
        //判断投递是否存在
        Application application=applicationMapper.findCompanyApplicationById(id);
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
            throw  new BusinessException("无权访问该投递");
        }
        return application;
    }

    @Override
    public void updateStatus(Long id, Long userId, ApplicationStatusUpdateDTO dto) {
        Company company=permissionService.requireApprovedCompany(userId);
        //判断投递是否存在
        Application application=applicationMapper.findCompanyApplicationById(id);
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
        Integer status=dto.getStatus();
        applicationMapper.updateStatus(id,status);
    }

}
