package com.recruit.smartrecruit.controller;

import com.recruit.smartrecruit.common.PageResult;
import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.dto.CompanyRegistrationReviewDecisionDTO;
import com.recruit.smartrecruit.entity.enums.CompanyReviewStatus;
import com.recruit.smartrecruit.service.CompanyRegistrationReviewService;
import com.recruit.smartrecruit.utils.ThreadLocalUtil;
import com.recruit.smartrecruit.vo.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class CompanyRegistrationReviewController {
    private final CompanyRegistrationReviewService service;

    public CompanyRegistrationReviewController(CompanyRegistrationReviewService service) {
        this.service = service;
    }

    @GetMapping("/admin/company/registration-reviews")
    public Result<PageResult<CompanyRegistrationReviewVO>> listReviews(
            @RequestParam(defaultValue = "PENDING") CompanyReviewStatus reviewStatus,
            @RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(service.listReviews(ThreadLocalUtil.getUserId(), reviewStatus, page, pageSize));
    }

    @GetMapping("/admin/company/registration-reviews/{reviewId}")
    public Result<CompanyRegistrationReviewDetailVO> getDetail(@PathVariable Long reviewId) {
        return Result.success(service.getDetail(ThreadLocalUtil.getUserId(), reviewId));
    }

    @PutMapping("/admin/company/registration-reviews/{reviewId}/decision")
    public Result<Void> decide(@PathVariable Long reviewId,
                              @RequestBody @Valid CompanyRegistrationReviewDecisionDTO dto) {
        service.decide(ThreadLocalUtil.getUserId(), reviewId, dto);
        return Result.success();
    }

    @GetMapping("/company/registration-reviews")
    public Result<PageResult<CompanyRegistrationReviewHistoryVO>> listMyHistory(
            @RequestParam(required = false) CompanyReviewStatus reviewStatus,
            @RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(service.listMyHistory(ThreadLocalUtil.getUserId(), reviewStatus, page, pageSize));
    }
}
