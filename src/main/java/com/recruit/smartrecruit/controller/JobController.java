package com.recruit.smartrecruit.controller;

import com.recruit.smartrecruit.annotation.OperationLog;
import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.entity.Job;
import com.recruit.smartrecruit.service.CompanyService;
import com.recruit.smartrecruit.service.JobService;
import com.recruit.smartrecruit.utils.ThreadLocalUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/job")
public class JobController {
    //注入JobService
    private  final JobService jobService;
    //注入CompanyService
    private final CompanyService companyService;
    public JobController(JobService jobService, CompanyService companyService) {
        this.jobService = jobService;
        this.companyService = companyService;
    }

    //发布岗位
    @OperationLog("发布岗位")
    @PostMapping
    public Result<Void> addJob(@RequestBody @Valid Job job){
        Map<String,Object> claims=ThreadLocalUtil.get();
        Long userId=((Number)claims.get("id")).longValue();
        jobService.add(job,userId);
        return Result.success();
    }
    //查询岗位列表
    @OperationLog("查询岗位列表")
    @GetMapping()
    public Result<List<Job>> listJob(){
        List<Job> jobs=jobService.findAllJob();
        return Result.success(jobs);
    }
    //查询岗位详情
    @OperationLog("查询岗位详情")
    @GetMapping("{id}")
    public Result<Job> infoJob(@PathVariable("id") long id){
        Job job=jobService.findById(id);
        return Result.success(job);
    }
    //修改岗位
    @OperationLog("修改岗位信息")
    @PutMapping
    public Result<Void> updateJob(@RequestBody Job newJob){
        Map<String,Object> claims=ThreadLocalUtil.get();
        Long userId=((Number)claims.get("id")).longValue();
        jobService.update(newJob,userId);
        return  Result.success();
    }
    //删除岗位
    @OperationLog("删除岗位")
    @DeleteMapping("{id}")
    public Result<Void> deleteJob(@PathVariable("id") long id){
        Map<String,Object> claims=ThreadLocalUtil.get();
        Long userId=((Number)claims.get("id")).longValue();
        jobService.delete(id,userId);
        return Result.success();
    }
}
