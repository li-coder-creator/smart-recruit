package com.recruit.smartrecruit.service;
import com.recruit.smartrecruit.dto.CompanyRegisterDTO;
import com.recruit.smartrecruit.entity.User;

import java.util.Map;

public interface UserService {
    // 根据用户名查询用户
    User findByUsername(String username) ;
    // 注册求职者用户
    void register(String username, String password) ;
    //注册企业用户
    Map<String,Object> registerCompany(CompanyRegisterDTO dto);
    //显示当前用户信息
    User findById(Long id);
    //修改当前用户
    void update(User user);
    //修改密码
    void updatePassword(
            Long id,
            String oldPassword,
            String newPassword
    );
}
