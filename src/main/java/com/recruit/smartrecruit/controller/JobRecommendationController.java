package com.recruit.smartrecruit.controller;

import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.dto.JobRecommendationQueryDTO;
import com.recruit.smartrecruit.service.JobRecommendationService;
import com.recruit.smartrecruit.utils.ThreadLocalUtil;
import com.recruit.smartrecruit.vo.RecommendationApiVO.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/job/recommendations")
public class JobRecommendationController {
    private final JobRecommendationService service;
    public JobRecommendationController(JobRecommendationService service) {this.service=service;}
    @GetMapping("/options") public Result<Options> options() {return Result.success(service.options(ThreadLocalUtil.getUserId()));}
    @GetMapping("/context") public Result<Context> context(@RequestParam(required=false) Long resumeId) {return Result.success(service.context(ThreadLocalUtil.getUserId(),resumeId));}
    @PostMapping public Result<QueryResult> query(@Valid @RequestBody JobRecommendationQueryDTO request) {return Result.success(service.query(ThreadLocalUtil.getUserId(),request));}
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> badQueryParameter() {return Result.error("请求参数格式不正确，请提供有效简历ID");}
}
