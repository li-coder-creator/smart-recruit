package com.recruit.smartrecruit.controller;

import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.common.PageResult;
import com.recruit.smartrecruit.dto.CompanyProfileUpdateDTO;
import com.recruit.smartrecruit.dto.CompanyRegisterDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.recruit.smartrecruit.service.CompanyService;
import com.recruit.smartrecruit.service.UserService;
import com.recruit.smartrecruit.utils.ThreadLocalUtil;
import com.recruit.smartrecruit.vo.CompanyProfileVO;
import com.recruit.smartrecruit.vo.CompanyPublicProfileVO;
import com.recruit.smartrecruit.vo.JobListVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("company")
public class CompanyController {
    private final CompanyService companyService;
    private final UserService userService;
    public CompanyController(CompanyService companyService, UserService userService) {
        this.companyService = companyService;
        this.userService = userService;
    }
    //注册企业用户
    @PostMapping("/register")
    public Result<Map<String,Object>> companyRegister(@RequestBody  @Valid CompanyRegisterDTO dto) {
        Map<String,Object> data=userService.registerCompany(dto);
        return Result.success(data);
    }
    // 企业账号与企业记录统一经注册事务创建，不再接收可控制准入状态的实体。
    @PostMapping()
    public ResponseEntity<Result<Void>> addCompany(){
        return ResponseEntity.status(HttpStatus.GONE)
                .body(Result.error("企业创建入口已停用，请使用企业注册接口"));
    }
    //查询当前用户企业
    @GetMapping
    public Result<CompanyProfileVO> getMyProfile() {
        Long userId = ThreadLocalUtil.getUserId();
        CompanyProfileVO profile = companyService.getMyProfile(userId);
        return Result.success(profile);
    }
    //修改企业
    @PutMapping
    public Result<Void> updateProfile(
            @RequestBody @Valid CompanyProfileUpdateDTO dto) {
        Long userId = ThreadLocalUtil.getUserId();
        companyService.updateProfile(userId, dto);
        return Result.success();
    }
    // 企业本人预览，无需准入已通过，使用公开字段响应。
    @GetMapping("/preview")
    public Result<CompanyPublicProfileVO> previewProfile() {
        return Result.success(companyService.previewProfile(ThreadLocalUtil.getUserId()));
    }

    // 求职者查看已通过准入的企业主页。
    @GetMapping("/{id}/profile")
    public Result<CompanyPublicProfileVO> getPublicProfile(@PathVariable("id") long id) {
        return Result.success(companyService.getPublicProfile(ThreadLocalUtil.getUserId(), id));
    }

    @GetMapping("/{id}/jobs")
    public Result<PageResult<JobListVO>> listActiveJobs(
            @PathVariable("id") long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(companyService.listActiveJobs(
                ThreadLocalUtil.getUserId(), id, page, pageSize));
    }

    // 保留岗位、投递和审核历史；注销业务需要独立生命周期设计。
    @DeleteMapping()
    public ResponseEntity<Result<Void>> deleteCompany(){
        return ResponseEntity.status(HttpStatus.GONE)
                .body(Result.error("企业物理删除入口已停用"));
    }


}
