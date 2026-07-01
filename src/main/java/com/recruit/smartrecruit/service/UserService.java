package com.recruit.smartrecruit.service;
import com.recruit.smartrecruit.entity.User;

public interface UserService {
    // 根据用户名查询用户
    User findByUsername(String username) ;
    // 注册用户
    void register(String username, String password) ;

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
