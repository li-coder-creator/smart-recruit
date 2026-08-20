package com.recruit.smartrecruit.controller;

import com.recruit.smartrecruit.annotation.OperationLog;
import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.dto.ApplicationApplyDTO;
import com.recruit.smartrecruit.dto.ApplicationStatusUpdateDTO;
import com.recruit.smartrecruit.service.ApplicationService;
import com.recruit.smartrecruit.utils.ThreadLocalUtil;
import com.recruit.smartrecruit.vo.ApplicationVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("application")
public class ApplicationController {
    //注入
    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }
    //求职者端
    //投递岗位
    @OperationLog("投递简历")
    @PostMapping
    public Result<Void> apply(@RequestBody@Valid ApplicationApplyDTO dto){
        Long userId = ThreadLocalUtil.getUserId();
        applicationService.apply(dto,userId);
        return Result.success();
    }
    //查询我的已投递
    @OperationLog("查询我的已投递")
    @GetMapping("/mine")
    public Result<List<ApplicationVO>> findJobseekerApplication(){
        Long userId = ThreadLocalUtil.getUserId();
        List<ApplicationVO> applications=applicationService.findJobseekerApplication(userId);
        return Result.success(applications);
    }

    //企业端
    //查看收到的投递
    @OperationLog("查看投递列表")
    @GetMapping("/my")
    public Result<List<ApplicationVO>> findCompanyApplication(){
        Long userId = ThreadLocalUtil.getUserId();
        List<ApplicationVO> applications=applicationService.findCompanyApplication(userId);
        return Result.success(applications);

    }

    //查看投递详情
    @OperationLog("查看投递详情")
    @GetMapping("/{id}")
    public Result<ApplicationVO> findApplicationDetail(@PathVariable ("id")Long id){
        Long userId = ThreadLocalUtil.getUserId();
        ApplicationVO application=applicationService.findApplicationDetail(id,userId);
        return Result.success(application);
    }
    //修改投递状态
    @OperationLog("修改投递状态")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id")Long id ,@RequestBody@Valid ApplicationStatusUpdateDTO dto){
        Long userId = ThreadLocalUtil.getUserId();
        applicationService.updateStatus(id,userId,dto);
        return Result.success();
    }



}
