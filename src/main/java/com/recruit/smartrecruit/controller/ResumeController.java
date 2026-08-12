package com.recruit.smartrecruit.controller;

import com.recruit.smartrecruit.annotation.OperationLog;
import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.entity.Resume;
import com.recruit.smartrecruit.service.ResumeService;
import com.recruit.smartrecruit.utils.ThreadLocalUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
        Map<String, Object> claims = ThreadLocalUtil.get();
        Long userId = ((Number) claims.get("id")).longValue();
        resume.setUserId(userId);
        resumeService.add(resume);
        return Result.success();
    }
    //获取当前用户所有简历
    @GetMapping()
    public Result<List<Resume>> list(){
        //获取当前登录用户
        Map<String, Object> claims = ThreadLocalUtil.get();
        Long userId = ((Number) claims.get("id")).longValue();
        List<Resume> resumes=resumeService.findByUserId(userId);
        return Result.success(resumes);
    }
    //查询简历详情
    @GetMapping("{id}")
    public Result<Resume> info(@PathVariable("id") Long Id){
        //获取当前登录用户
        Map<String,Object> claims=ThreadLocalUtil.get();
        Long userId=((Number)claims.get("id")).longValue();
        Resume resume=resumeService.findById(Id,userId);
        return Result.success(resume);
    }
    //修改简历
    @OperationLog("修改简历")
    @PutMapping()
    public Result<Void> updateResume(@RequestBody @Valid Resume newResume){
        //获取当前登录用户
        Map<String,Object> claims=ThreadLocalUtil.get();
        Long userId=((Number)claims.get("id")).longValue();
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
        Map<String,Object> claims=ThreadLocalUtil.get();
        Long userId=((Number)claims.get("id")).longValue();
        resumeService.delete(id,userId);
        return Result.success();

    }


}
