package com.recruit.smartrecruit.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.recruit.smartrecruit.common.PageResult;
import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.User;
import com.recruit.smartrecruit.entity.enums.CompanyStatus;
import com.recruit.smartrecruit.entity.enums.UserRole;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.CompanyMapper;
import com.recruit.smartrecruit.mapper.UserMapper;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.service.AdminService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminServiceImpl implements AdminService {
    private final CompanyMapper companyMapper;
    private final PermissionService permissionService;
    private final UserMapper userMapper;

    public AdminServiceImpl(CompanyMapper companyMapper, PermissionService permissionService, UserMapper userMapper) {
        this.companyMapper = companyMapper;
        this.permissionService = permissionService;
        this.userMapper = userMapper;
    }

    @Override
    public PageResult<Company> findPendingCompanies(Long userId,Integer page,Integer pageSize){
        permissionService.requireAdmin(userId);
        PageHelper.startPage(page,pageSize);
        List<Company> companies=companyMapper.findByStatus(CompanyStatus.PENDING);
        PageInfo<Company> pageInfo=new PageInfo<>(companies);
        return new PageResult<>(pageInfo.getTotal(),pageInfo.getList());
    }
    @Override
    public Company findCompanyById(Long userId, Long companyId) {
        permissionService.requireAdmin(userId);
        Company company=companyMapper.findCompanyById(companyId);
        if(company==null){
            throw new BusinessException("企业不存在");
        }
        return company;
    }
    public PageResult<Company> findAllCompany(Long userId, CompanyStatus companyStatus,Integer page,Integer pageSize) {
        permissionService.requireAdmin(userId);
        PageHelper.startPage(page,pageSize);
        List<Company> companies=companyMapper.findAllCompany(companyStatus);
        PageInfo<Company> pageInfo=new PageInfo<>(companies);
        return new PageResult<>(pageInfo.getTotal(),pageInfo.getList());
    }

    //审核通过或拒绝
    @Override
    public void updateCompanyStatus(Long userId, Long id, CompanyStatus companyStatus) {
        permissionService.requireAdmin(userId);
        Company company = companyMapper.findCompanyById(id);
        if (company == null) {
            throw new BusinessException("企业不存在");
        }
        companyMapper.updateStatus(id, companyStatus);
    }

    @Override
    public PageResult<User> findAllUsers(Long userId, UserRole userRole, Integer page, Integer pageSize) {
        permissionService.requireAdmin(userId);
        PageHelper.startPage(page,pageSize);
        List<User> users=userMapper.findAllUsers(userRole);
        PageInfo<User> pageInfo=new PageInfo<>(users);
        return new PageResult<>(pageInfo.getTotal(),pageInfo.getList());
    }

    @Override
    public User findUserDetailById(Long userId, Long id) {
        permissionService.requireAdmin(userId);
        User user=userMapper.findById(id);
        if(user==null){
            throw new BusinessException("用户不存在");
        }
        return user;
    }
}
