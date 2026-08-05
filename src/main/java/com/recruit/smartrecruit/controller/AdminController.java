package com.recruit.smartrecruit.controller;

import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.enums.CompanyStatus;
import com.recruit.smartrecruit.service.AdminService;
import com.recruit.smartrecruit.service.CompanyService;
import com.recruit.smartrecruit.utils.ThreadLocalUtil;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public Result<List<Company>> findPendingCompanies(){
        List<Company> companies=adminService.findPendingCompanies();
        return Result.success(companies);
    }
    //企业审核通过
    @PutMapping("/company/{id}/approve")
    public Result<Void> CompanyApprovedStatus(@PathVariable Long id){
        adminService.updateCompanyStatus(id, CompanyStatus.APPROVED);
        return Result.success();
    }
    //企业审核拒绝
    @PutMapping("/company/{id}/reject")
    public Result<Void> CompanyRejectedStatus(@PathVariable Long id){
        adminService.updateCompanyStatus(id, CompanyStatus.REJECTED);
        return Result.success();
    }
}
