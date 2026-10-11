package com.recruit.smartrecruit.controller;

import com.recruit.smartrecruit.annotation.OperationLog;
import com.recruit.smartrecruit.common.PageResult;
import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.dto.JobSaveDTO;
import com.recruit.smartrecruit.entity.Job;
import com.recruit.smartrecruit.entity.enums.JobListFilter;
import com.recruit.smartrecruit.service.CompanyService;
import com.recruit.smartrecruit.service.JobService;
import com.recruit.smartrecruit.utils.ThreadLocalUtil;
import com.recruit.smartrecruit.vo.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    //新建并发布岗位
    @OperationLog("发布岗位")
    @PostMapping
    public Result<Void> addJob(@RequestBody @Valid JobSaveDTO jobSaveDTO){
        Long userId = ThreadLocalUtil.getUserId();
        jobService.add(jobSaveDTO, userId);
        return Result.success();
    }
    //保存新草稿
    @OperationLog("保存新草稿")
    @PostMapping("/drafts")
    public Result<Void> saveDraft(@RequestBody @Valid JobSaveDTO jobSaveDTO){
        Long userId = ThreadLocalUtil.getUserId();
        jobService.saveDraft(jobSaveDTO, userId);
        return Result.success();
    }
    //发布已有草稿
    @OperationLog("发布已有草稿")
    @PutMapping("/{id}/publish")
    public Result<Void> publishDraft(@PathVariable("id") long id){
        Long userId = ThreadLocalUtil.getUserId();
        jobService.publishDraft(id, userId);
        return Result.success();
    }

    //修改岗位
    @OperationLog("修改岗位信息")
    @PutMapping("{id}")
    public Result<Void> updateJob(@RequestBody @Valid JobSaveDTO jobSaveDTO,@PathVariable("id") long id){
        Long userId = ThreadLocalUtil.getUserId();
        jobService.update(jobSaveDTO, id, userId);
        return Result.success();
    }

    //暂停招聘
    @OperationLog("暂停招聘")
    @PutMapping("{id}/pause")
    public Result<Void> pauseJob(@PathVariable("id") long id){
        Long userId = ThreadLocalUtil.getUserId();
        jobService.pauseJob(id, userId);
        return Result.success();
    }
    //恢复招聘
    @OperationLog("恢复招聘")
    @PutMapping("{id}/resume")
    public Result<Void> resumeJob(@PathVariable("id") long id){
        Long userId = ThreadLocalUtil.getUserId();
        jobService.resumeJob(id, userId);
        return Result.success();
    }
    //结束招聘
    @OperationLog("结束招聘")
    @PutMapping("{id}/end")
    public Result<Void> endJob(@PathVariable("id") long id){
        Long userId = ThreadLocalUtil.getUserId();
        jobService.endJob(id, userId);
        return Result.success();
    }
    //删除草稿
    @OperationLog("删除草稿")
    @DeleteMapping("{id}")
    public Result<Void> deleteJob(@PathVariable("id") long id){
        Long userId = ThreadLocalUtil.getUserId();
        jobService.delete(id, userId);
        return Result.success();
    }


    //求职者
    //查询岗位列表(分页)
    @OperationLog("查询岗位列表")
    @GetMapping()
    public Result<PageResult<JobListVO>> listJob(@RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(defaultValue = "10") Integer size,
                                                   @RequestParam(required = false) String keyword
                                     ){
        Long userId = ThreadLocalUtil.getUserId();
        PageResult<JobListVO> jobs=jobService.findJob(userId, page, size, keyword);
        return Result.success(jobs);
    }
    //查询岗位详情
    @OperationLog("查询岗位详情")
    @GetMapping("{id}")
    public Result<JobDetailVO> infoActiveJob(@PathVariable("id") long id){
        Long userId = ThreadLocalUtil.getUserId();
        JobDetailVO job=jobService.findById(id, userId);
        return Result.success(job);
    }

    //企业
    // 查询岗位列表(分页)
    @OperationLog("公司查询发布的岗位")
    @GetMapping("/company")
    public Result<PageResult<CompanyJobListVO>> listCompanyJob(@RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "10") Integer size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "ALL") JobListFilter filter){
        Long userId = ThreadLocalUtil.getUserId();
        PageResult<CompanyJobListVO> jobs = jobService.findCompanyJob(userId, page, size, keyword, filter);
        return Result.success(jobs);
    }

    //查询岗位详情
    @OperationLog("查询岗位详情")
    @GetMapping("/company/{id}")
    public Result<JobDetailVO> infoJob(@PathVariable("id") long id){
        Long userId = ThreadLocalUtil.getUserId();
        JobDetailVO job=jobService.findByCompanyId(id, userId);
        return Result.success(job);
    }

    //企业招聘总览
    @OperationLog("企业招聘总览")
    @GetMapping("/company/overview")
    public Result<CompanyJobOverviewVO> companyOverview(){
        Long userId = ThreadLocalUtil.getUserId();
        CompanyJobOverviewVO companyJobOverviewVO = jobService.companyOverview(userId);
        return Result.success(companyJobOverviewVO);
    }

    //单岗位招聘进展
    @OperationLog("单岗位招聘进展")
    @GetMapping("/company/{id}/progress")
    public Result<JobRecruitmentProgressVO> companyJobProgress(@PathVariable("id") long id){
        Long userId = ThreadLocalUtil.getUserId();
        JobRecruitmentProgressVO jobRecruitmentProgressVO = jobService.findJobRecruitmentProgressById(id, userId);
        return Result.success(jobRecruitmentProgressVO);
    }

    //单岗位最近投递
    @OperationLog("单岗位最近投递")
    @GetMapping("/company/{id}/recent-applications")
    public Result<List<RecentApplicationVO>> companyJobRecent(
            @PathVariable("id") long id,
            @RequestParam(required = false) Integer limit) {

        Long userId = ThreadLocalUtil.getUserId();
        List<RecentApplicationVO> items = jobService.findJobRecentById(id, userId, limit);
        return Result.success(items);
    }

    //单岗位操作记录
    @OperationLog("单岗位操作记录")
    @GetMapping("/company/{id}/activities")
    public Result<List<JobActivityVO>> companyJobOperationRecord(@PathVariable("id") long id,
                                                                 @RequestParam(required = false) Integer limit){
        Long userId = ThreadLocalUtil.getUserId();
        List<JobActivityVO> jobActivityVOList = jobService.findJobActivityById(id, userId, limit);
        return Result.success(jobActivityVOList );
    }
}
