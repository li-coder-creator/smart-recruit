package com.recruit.smartrecruit.permission;

import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.User;
import com.recruit.smartrecruit.entity.enums.CompanyStatus;
import com.recruit.smartrecruit.entity.enums.UserRole;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.CompanyMapper;
import com.recruit.smartrecruit.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class PermissionServiceImpl implements PermissionService{
    private final CompanyMapper companyMapper;
    private final UserMapper userMapper;
    public PermissionServiceImpl(CompanyMapper companyMapper, UserMapper userMapper) {
        this.companyMapper = companyMapper;
        this.userMapper = userMapper;
    }

    private User getUser(Long userId) {
        //角色判断
        User user=userMapper.findById(userId);
        if(user==null){
            throw new BusinessException("用户不存在");
        }
        return user;
    }
    @Override
    public User requireJobSeeker(Long userId) {
        User user=getUser(userId);
        if(user.getRole()!=UserRole.JOB_SEEKER){
            throw new BusinessException("仅求职者可访问");
        }
        return user;
    }

    @Override
    public Company requireCompany(Long userId) {
        //角色判断
        User user=getUser(userId);
        if(user.getRole()!=UserRole.COMPANY){
            throw new BusinessException("仅企业用户可访问");
        }
        //判断企业是否存在
        Company company=companyMapper.findByUserId(userId);
        if(company==null){
            throw  new BusinessException("企业不存在");
        }
        return company;
    }

    @Override
    public Company requireApprovedCompany(Long userId) {
        //角色判断
        Company company=requireCompany(userId);
        //判断企业认证状态
        if(company.getStatus()!= CompanyStatus.APPROVED){
            throw new BusinessException("企业未通过验证");
        }
        return company;
    }

    @Override
    public User requireAdmin(Long userId) {
        //角色判断
        User user=getUser(userId);
        if(user.getRole()!=UserRole.ADMIN){
            throw new BusinessException("仅管理员可访问");
        }
        return user;
    }
}
