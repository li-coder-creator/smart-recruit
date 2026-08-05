package com.recruit.smartrecruit.service.impl;

import com.recruit.smartrecruit.dto.CompanyRegisterDTO;
import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.User;
import com.recruit.smartrecruit.entity.enums.CompanyStatus;
import com.recruit.smartrecruit.entity.enums.UserRole;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.CompanyMapper;
import com.recruit.smartrecruit.mapper.UserMapper;
import com.recruit.smartrecruit.service.UserService;
import com.recruit.smartrecruit.utils.Md5Util;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final CompanyMapper companyMapper;
    public UserServiceImpl(UserMapper userMapper, CompanyMapper companyMapper) {
        this.userMapper = userMapper;
        this.companyMapper = companyMapper;
    }

    // 根据用户名查询用户
    @Override
    public User findByUsername(String username) {
        return userMapper.findByUsername(username);
    }
    // 注册求职者用户
    @Override
    public void register(String username, String password) {
        //加密密码
        String encryptPassword = Md5Util.encrypt(password);
        User user = new User();
        user.setUsername(username);
        user.setPassword(encryptPassword);
        user.setRole(UserRole.JOB_SEEKER);
        //添加到数据库
        userMapper.add(user);
    }
    //注册企业用户
    @Override
    @Transactional
    public void registerCompany(CompanyRegisterDTO dto) {
        User existUser = userMapper.findByUsername(dto.getUsername());
        if (existUser != null) {
            throw new BusinessException("用户名已存在");
        }
        //加密密码
        String encryptPassword = Md5Util.encrypt(dto.getPassword());
        //用户注册
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(encryptPassword);
        user.setRole(UserRole.COMPANY);
        //添加到数据库
        userMapper.add(user);
        //创建 Company
        Company company = new Company();
        company.setUserId(user.getId());
        company.setName(dto.getCompanyName());
        company.setDescription(dto.getDescription());
        company.setLogo(dto.getLogo());
        company.setCity(dto.getCity());
        company.setAddress(dto.getAddress());
        // 企业注册默认待审核
        company.setStatus(CompanyStatus.PENDING);
        //保存 Company
        companyMapper.add(company);
    }
    //显示当前用户信息
    @Override
    public User findById(Long id) {
        return userMapper.findById(id);
    }
    //修改当前用户
    @Override
    public void update(User user) {
        userMapper.update(user);
    }
    //修改密码
    @Override
    public void updatePassword(Long id, String oldPassword, String newPassword) {
        //查询当前用户
        User user=userMapper.findById(id);
        if(user == null){
            throw new BusinessException("用户不存在");
        }
        //MD5旧密码
        String md5OldPassword = Md5Util.encrypt(oldPassword);
        //判断
        if (md5OldPassword.equals(user.getPassword())) {
            //验证成功
            //MD5新密码
            String md5NewPassword = Md5Util.encrypt(newPassword);
            //调用Mapper,更新数据库
            userMapper.updatePassword(id,md5NewPassword);
        } else {
            //验证失败
            throw new BusinessException("原密码错误");
        }

    }
}