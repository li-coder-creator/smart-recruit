package com.recruit.smartrecruit.service.impl;

import com.recruit.smartrecruit.entity.User;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.UserMapper;
import com.recruit.smartrecruit.service.UserService;
import com.recruit.smartrecruit.utils.Md5Util;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    // 根据用户名查询用户
    @Override
    public User findByUsername(String username) {
        return userMapper.findByUsername(username);
    }
    // 注册用户
    @Override
    public void register(String username, String password) {
        //加密密码
        String encryptPassword = Md5Util.encrypt(password);
        //添加到数据库
        userMapper.add(username, encryptPassword);
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