package com.recruit.smartrecruit.controller;

import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.dto.ApplicationApplyDTO;
import com.recruit.smartrecruit.dto.ApplicationStatusUpdateDTO;
import com.recruit.smartrecruit.entity.Application;
import com.recruit.smartrecruit.service.ApplicationService;
import com.recruit.smartrecruit.utils.ThreadLocalUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
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
    @PostMapping
    public Result<Void> apply(@RequestBody@Valid ApplicationApplyDTO dto){
        Map<String,Object> claims= ThreadLocalUtil.get();
        Long userId=((Number)claims.get("id")).longValue();
        applicationService.apply(dto,userId);
        return Result.success();
    }
    //企业端
    //查看收到的投递
    @GetMapping("/my")
    public Result<List<Application>> findCompanyApplication(){
        Map<String,Object> claims=ThreadLocalUtil.get();
        Long userId=((Number)claims.get("id")).longValue();
        List<Application> applications=applicationService.findCompanyApplication(userId);
        return Result.success(applications);

    }
    //查看投递详情
    @GetMapping("/{id}")
    public Result<Application> findCompanyApplicationById(@PathVariable ("id")Long id){
        Map<String,Object> claims=ThreadLocalUtil.get();
        Long userId=((Number)claims.get("id")).longValue();
        Application application=applicationService.findCompanyApplicationById(id,userId);
        return Result.success(application);
    }
    //修改投递状态
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id")Long id ,@RequestBody@Valid ApplicationStatusUpdateDTO dto){
        Map<String,Object> claims=ThreadLocalUtil.get();
        Long userId=((Number)claims.get("id")).longValue();
        applicationService.updateStatus(id,userId,dto);
        return Result.success();
    }



}
