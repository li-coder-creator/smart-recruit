package com.recruit.smartrecruit.service;

import com.recruit.smartrecruit.entity.Resume;

import java.util.List;

public interface ResumeService {
    //添加简历
    void add(Resume resume);
    //获取当前用户所有简历
    List<Resume> findByUserId(Long userId);
    //查询简历详情
    Resume findById(Long id, Long userId);
    //修改简历
    void update(Resume newResume);
    //删除简历
    void delete(Long id,Long userId);
}
