package com.recruit.smartrecruit.service.impl;

import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.Job;
import com.recruit.smartrecruit.entity.enums.CompanyStatus;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.CompanyMapper;
import com.recruit.smartrecruit.mapper.JobMapper;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.service.JobService;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public class JobServiceImpl implements JobService {
   //注入
    private  final JobMapper jobMapper;
    private final PermissionService permissionService;

    public JobServiceImpl(JobMapper jobMapper, PermissionService permissionService) {
        this.jobMapper = jobMapper;
        this.permissionService = permissionService;
    }

    @Override
    public void add(Job job, Long userId) {
     Company company=permissionService.requireApprovedCompany(userId);
     Long companyId=company.getId();
     job.setCompanyId(companyId);
     jobMapper.add(job);
    }

    @Override
    public List<Job> findAllJob() {
     return jobMapper.findAllJob();
    }

    @Override
    public Job findById(long id) {
    //判单岗位是否存在
     Job job=jobMapper.findById(id);
     if(job==null){
      throw new BusinessException("岗位不存在");
     }
     return job;
    }

    @Override
    public void update(Job newJob, Long userId) {
     Company company=permissionService.requireApprovedCompany(userId);
     //权限校验
     Job job=jobMapper.findById(newJob.getId());
     if(job == null){
      throw new BusinessException("岗位不存在");
     }
     if(!job.getCompanyId().equals(company.getId())){
      throw new BusinessException("没有修改权限");
     }
     jobMapper.update(newJob);
    }

    @Override
    public void delete(long id, Long userId) {
     Company company=permissionService.requireApprovedCompany(userId);
     //权限校验
     Job job=jobMapper.findById(id);
     if(job == null){
      throw new BusinessException("岗位不存在");
     }
     if(!job.getCompanyId().equals(company.getId())){
      throw new BusinessException("没有删除权限");
     }
     jobMapper.delete(id);
    }
}
