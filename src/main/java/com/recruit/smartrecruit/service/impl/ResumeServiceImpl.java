package com.recruit.smartrecruit.service.impl;

import com.recruit.smartrecruit.entity.Resume;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.ResumeMapper;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.service.ResumeService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ResumeServiceImpl implements ResumeService {
    private final ResumeMapper resumeMapper;
    private final PermissionService permissionService;

    public ResumeServiceImpl(ResumeMapper resumeMapper, PermissionService permissionService) {
        this.resumeMapper = resumeMapper;
        this.permissionService = permissionService;
    }

    //添加简历
    @Override
    public void add(Resume resume) {
        permissionService.requireJobSeeker(resume.getUserId());
        resumeMapper.add(resume);
    }
    //获取当前用户所有简历
    @Override
    public List<Resume> findByUserId(Long userId) {
        permissionService.requireJobSeeker(userId);
        return resumeMapper.findByUserId(userId);
    }
    //查询简历详情
    @Override
    public Resume findById(Long id, Long userId) {
        permissionService.requireJobSeeker(userId);
        //从数据库拿到查询简历对应的实体
        Resume resume=resumeMapper.findById(id);
        if (resume == null) {
            throw new BusinessException("简历不存在");
        }
        //判断查询简历是当前用户的简历
        if (!resume.getUserId().equals(userId)){
            throw new BusinessException("无权限访问");
        }
        return resume;
    }
    //修改简历
    @Override
    public void update(Resume newResume) {
        permissionService.requireJobSeeker(newResume.getUserId());
        //从数据库拿到查询简历对应的实体
        Resume resume=resumeMapper.findById(newResume.getId());
        if (resume == null) {
            throw new BusinessException("简历不存在");
        }
        //判断查询简历是当前用户的简历
        if (!resume.getUserId().equals(newResume.getUserId())){
            throw new BusinessException("无权限访问");
        }
        resumeMapper.update(newResume);

    }
    //删除简历
    @Override
    public void delete(Long id,Long userId) {
        permissionService.requireJobSeeker(userId);
        //从数据库拿到查询简历对应的实体
        Resume resume=resumeMapper.findById(id);
        if (resume == null) {
            throw new BusinessException("简历不存在");
        }
        //判断查询简历是当前用户的简历
        if (!resume.getUserId().equals(userId)){
            throw new BusinessException("无权限访问");
        }
        resumeMapper.delete(id);
    }
}
