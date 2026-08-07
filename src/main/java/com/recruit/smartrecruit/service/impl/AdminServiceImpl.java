package com.recruit.smartrecruit.service.impl;

import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.enums.CompanyStatus;
import com.recruit.smartrecruit.mapper.CompanyMapper;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.service.AdminService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminServiceImpl implements AdminService {
    private final CompanyMapper companyMapper;
    private final PermissionService permissionService;

    public AdminServiceImpl(CompanyMapper companyMapper, PermissionService permissionService) {
        this.companyMapper = companyMapper;
        this.permissionService = permissionService;
    }

    @Override
    public List<Company> findPendingCompanies(Long userId){
        permissionService.requireAdmin(userId);
        return companyMapper.findByStatus(CompanyStatus.PENDING);
    }
    //审核通过或拒绝
    @Override
    public void updateCompanyStatus(Long userId, Long id, CompanyStatus companyStatus) {
        permissionService.requireAdmin(userId);
        companyMapper.updateStatus(id,companyStatus);
    }

}
