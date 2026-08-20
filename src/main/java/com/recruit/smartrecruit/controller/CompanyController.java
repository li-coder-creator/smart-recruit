package com.recruit.smartrecruit.controller;

import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.dto.CompanyRegisterDTO;
import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.User;
import com.recruit.smartrecruit.service.CompanyService;
import com.recruit.smartrecruit.service.UserService;
import com.recruit.smartrecruit.utils.ThreadLocalUtil;
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
    //创建企业
    @PostMapping()
    public Result<Void> addCompany(@RequestBody @Valid Company company){
        //获取当前登录用户
        Long userId = ThreadLocalUtil.getUserId();
        company.setUserId(userId);
        companyService.add(company);
        return Result.success();
    }
    //查询当前用户企业
    @GetMapping()
    public Result<Company> getMyCompany(){
        Long userId = ThreadLocalUtil.getUserId();
        Company company=companyService.findByUserId(userId);
        return Result.success(company);
    }
    //修改企业
    @PutMapping()
    public Result<Void> updateCompany(@RequestBody Company newCompany){
        Long userId = ThreadLocalUtil.getUserId();
        newCompany.setUserId(userId);
        companyService.update(newCompany);
        return Result.success();
    }
    //删除企业
    @DeleteMapping()
    public Result<Void> deleteCompany(){
        Long userId = ThreadLocalUtil.getUserId();
        companyService.delete(userId);
        return Result.success();
    }


}
