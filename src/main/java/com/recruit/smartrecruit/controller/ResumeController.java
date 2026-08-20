package com.recruit.smartrecruit.controller;

import com.recruit.smartrecruit.annotation.OperationLog;
import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.entity.Resume;
import com.recruit.smartrecruit.service.ResumeService;
import com.recruit.smartrecruit.utils.ThreadLocalUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/resume")
public class ResumeController {
    private final ResumeService resumeService;
    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }
    //添加简历
    @OperationLog("添加简历")
    @PostMapping()
    public Result<Void> addResume(@RequestBody @Valid Resume resume) {
        //获取当前登录用户
        Long userId = ThreadLocalUtil.getUserId();
        resume.setUserId(userId);
        resumeService.add(resume);
        return Result.success();
    }
    //获取当前用户所有简历
    @GetMapping()
    public Result<List<Resume>> list(){
        //获取当前登录用户
        Long userId = ThreadLocalUtil.getUserId();
        List<Resume> resumes=resumeService.findByUserId(userId);
        return Result.success(resumes);
    }
    //查询简历详情
    @GetMapping("{id}")
    public Result<Resume> info(@PathVariable("id") Long id){
        Long userId = ThreadLocalUtil.getUserId();
        Resume resume=resumeService.findById(id,userId);
        return Result.success(resume);
    }
    //修改简历
    @OperationLog("修改简历")
    @PutMapping()
    public Result<Void> updateResume(@RequestBody @Valid Resume newResume){
        //获取当前登录用户
        Long userId = ThreadLocalUtil.getUserId();
        //设置用户ID用于权限校验
        newResume.setUserId(userId);
        resumeService.update(newResume);
        return Result.success();
    }
    //删除简历
    @OperationLog("删除简历")
    @DeleteMapping("{id}")
    public Result<Void> deleteResume(@PathVariable("id") Long id){
        //获取用户id
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.delete(id,userId);
        return Result.success();

    }


}
