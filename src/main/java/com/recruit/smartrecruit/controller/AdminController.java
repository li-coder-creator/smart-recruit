package com.recruit.smartrecruit.controller;

import com.recruit.smartrecruit.common.PageResult;
import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.User;
import com.recruit.smartrecruit.entity.enums.CompanyStatus;
import com.recruit.smartrecruit.entity.enums.UserRole;
import com.recruit.smartrecruit.service.AdminService;
import com.recruit.smartrecruit.service.CompanyService;
import com.recruit.smartrecruit.utils.ThreadLocalUtil;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("admin")
public class AdminController {
    //注入
    public final AdminService adminService;
    public final CompanyService companyService;
    public AdminController(AdminService adminService, CompanyService companyService) {
        this.adminService = adminService;
        this.companyService = companyService;
    }

    //查询待审核企业
    @GetMapping("/company/pending")
    public Result<PageResult<Company>> findPendingCompanies(@RequestParam(defaultValue = "1") Integer page,
                                                            @RequestParam(defaultValue = "10") Integer pageSize){
        Map<String,Object> claims=ThreadLocalUtil.get();
        Long userId=((Number)claims.get("id")).longValue();
        PageResult<Company> pageResult=adminService.findPendingCompanies(userId,page,pageSize);
        return Result.success(pageResult);
    }
    //查询所有公司（分页＋条件查询）
    @GetMapping("/company")
    public Result<PageResult<Company>> findAllCompany(@RequestParam(defaultValue = "1") Integer page,
                                                      @RequestParam(defaultValue = "10") Integer pageSize,
                                                      @RequestParam(required = false)CompanyStatus companyStatus){
        Map<String,Object> claims=ThreadLocalUtil.get();
        Long userId=((Number)claims.get("id")).longValue();
        PageResult<Company> pageResult=adminService.findAllCompany(userId,companyStatus,page,pageSize);
        return Result.success(pageResult);
    }
    //企业详情
    @GetMapping("/company/{id}")
    public Result<Company> findCompanyById(@PathVariable("id") Long companyId){
        Map<String,Object> claims=ThreadLocalUtil.get();
        Long userId=((Number)claims.get("id")).longValue();
        Company company=adminService.findCompanyById(userId,companyId);
        return Result.success(company);
    }
    //企业审核通过
    @PutMapping("/company/{id}/approve")
    public Result<Void> CompanyApprovedStatus(@PathVariable Long id){
        Map<String,Object> claims=ThreadLocalUtil.get();
        Long userId=((Number)claims.get("id")).longValue();
        adminService.updateCompanyStatus(userId, id, CompanyStatus.APPROVED);
        return Result.success();
    }
    //企业审核拒绝
    @PutMapping("/company/{id}/reject")
    public Result<Void> CompanyRejectedStatus(@PathVariable Long id){
        Map<String,Object> claims=ThreadLocalUtil.get();
        Long userId=((Number)claims.get("id")).longValue();
        adminService.updateCompanyStatus(userId, id, CompanyStatus.REJECTED);
        return Result.success();
    }
    //查询用户列表（分页＋条件查询）
    @GetMapping("/user")
    public Result<PageResult<User>> findAllUsers(@RequestParam(defaultValue = "1")Integer page,
                                                @RequestParam(defaultValue = "10")Integer pageSize,
                                                @RequestParam(required = false)UserRole userRole
                                                ){
        Map<String,Object> claims=ThreadLocalUtil.get();
        Long userId=((Number)claims.get("id")).longValue();
        PageResult<User> pageResult=adminService.findAllUsers(userId,userRole,page,pageSize);
        return Result.success(pageResult);
    }
    //查询用户详情
    @GetMapping("user/{id}")
    public Result<User> findUserDetailById(@PathVariable("id")Long id){
        Map<String,Object> claims=ThreadLocalUtil.get();
        Long userId=((Number)claims.get("id")).longValue();
        User user=adminService.findUserDetailById(userId,id);
        return Result.success(user);
    }


}
