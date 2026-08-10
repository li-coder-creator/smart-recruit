package com.recruit.smartrecruit.service;

import com.github.pagehelper.Page;
import com.recruit.smartrecruit.common.PageResult;
import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.User;
import com.recruit.smartrecruit.entity.enums.CompanyStatus;
import com.recruit.smartrecruit.entity.enums.UserRole;

import java.util.List;

public interface AdminService {
    PageResult<Company> findPendingCompanies(Long userId, Integer page, Integer pageSize);
    void updateCompanyStatus(Long userdId,Long id, CompanyStatus companyStatus);
    Company findCompanyById(Long userId, Long companyId);
    PageResult<Company> findAllCompany(Long userId,CompanyStatus companyStatus, Integer page,Integer pageSize);

    PageResult findAllUsers(Long userId, UserRole userRole, Integer page, Integer pageSize);

    User findUserDetailById(Long userId, Long id);
}
