package com.recruit.smartrecruit.service.impl;

import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.User;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.CompanyMapper;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.service.CompanyService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class CompanyServiceImpl implements CompanyService {
    private final CompanyMapper companyMapper;
    private final PermissionService permissionService;

    public CompanyServiceImpl(CompanyMapper companyMapper, PermissionService permissionService) {
        this.companyMapper = companyMapper;
        this.permissionService = permissionService;
    }

    @Override
    public void add(Company company) {
        companyMapper.add(company);
    }

    @Override
    public Company findByUserId(Long userId) {
        return companyMapper.findByUserId(userId);
    }

    @Override
    public void update(Company newCompany) {
        //查询当前用户的企业
        Long userId=newCompany.getUserId();
        Company company=permissionService.requireCompany(userId);
        //userId 负责确认“谁有权限”，companyId 负责确认“修改哪条数据”。
        Long companyId=company.getId();
        newCompany.setId(companyId);
        companyMapper.update(newCompany);
    }

    @Override
    public void delete(Long userId) {
        Company company=permissionService.requireCompany(userId);
        Long companyId=company.getId();
        companyMapper.delete(companyId);
    }
}
