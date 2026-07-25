package com.recruit.smartrecruit.service.impl;

import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.Job;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.CompanyMapper;
import com.recruit.smartrecruit.mapper.JobMapper;
import com.recruit.smartrecruit.service.JobService;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public class JobServiceImpl implements JobService {
   //注入
    private final JobMapper jobMapper;
    private final CompanyMapper companyMapper;
    public JobServiceImpl(JobMapper jobMapper, CompanyMapper companyMapper) {
        this.jobMapper = jobMapper;
     this.companyMapper = companyMapper;
    }

    @Override
    public void add(Job job, Long userId) {
    //判断企业是否存在
     Company company=companyMapper.findByUserId(userId);
     if(company==null){
      throw  new BusinessException("企业不存在");
     }
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
     //判断企业是否存在
     Company company=companyMapper.findByUserId(userId);
     if(company==null){
      throw  new BusinessException("企业不存在");
     }
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
     //判断企业是否存在
     Company company=companyMapper.findByUserId(userId);
     if(company==null){
      throw  new BusinessException("企业不存在");
     }
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
