package com.recruit.smartrecruit.controller;
import com.recruit.smartrecruit.common.*;
import com.recruit.smartrecruit.dto.*;
import com.recruit.smartrecruit.entity.enums.*;
import com.recruit.smartrecruit.service.CompanyQualificationReviewService;
import com.recruit.smartrecruit.utils.ThreadLocalUtil;
import com.recruit.smartrecruit.vo.*;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class CompanyQualificationReviewController {
    private final CompanyQualificationReviewService service;
    public CompanyQualificationReviewController(CompanyQualificationReviewService service){this.service=service;}
    @GetMapping("/admin/company/qualification-reviews")
    public Result<PageResult<CompanyQualificationReviewVO>> list(@RequestParam(defaultValue="PENDING") CompanyReviewStatus reviewStatus,
        @RequestParam(required=false) CompanyQualificationType qualificationType,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int pageSize) {
        return Result.success(service.list(ThreadLocalUtil.getUserId(),reviewStatus,qualificationType,page,pageSize));
    }
    @GetMapping("/admin/company/qualification-reviews/{reviewId}")
    public Result<CompanyQualificationReviewDetailVO> detail(@PathVariable Long reviewId){return Result.success(service.detail(ThreadLocalUtil.getUserId(),reviewId));}
    @GetMapping("/admin/company/qualification-reviews/{reviewId}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long reviewId){
        var f=service.readFile(ThreadLocalUtil.getUserId(),reviewId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType())).contentLength(f.bytes().length)
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(f.filename(),StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options","nosniff").cacheControl(CacheControl.noStore()).body(f.bytes());
    }
    @PutMapping("/admin/company/qualification-reviews/{reviewId}/decision")
    public Result<Void> decide(@PathVariable Long reviewId,@RequestBody @Valid CompanyQualificationReviewDecisionDTO dto){
        service.decide(ThreadLocalUtil.getUserId(),reviewId,dto);return Result.success();
    }
    @GetMapping("/company/qualification-reviews")
    public Result<PageResult<CompanyQualificationReviewHistoryVO>> history(@RequestParam(required=false) CompanyReviewStatus reviewStatus,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int pageSize){
        return Result.success(service.history(ThreadLocalUtil.getUserId(),reviewStatus,page,pageSize));
    }
}
