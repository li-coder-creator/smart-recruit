package com.recruit.smartrecruit.controller;

import com.recruit.smartrecruit.annotation.OperationLog;
import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.dto.UserPasswordDTO;
import com.recruit.smartrecruit.dto.UserUpdateDTO;
import com.recruit.smartrecruit.entity.User;
import com.recruit.smartrecruit.service.UserService;
import com.recruit.smartrecruit.utils.JwtUtil;
import com.recruit.smartrecruit.utils.Md5Util;
import com.recruit.smartrecruit.utils.ThreadLocalUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {
    //注入UserService
    //final 修饰的成员变量，一旦被赋值，就不能被再次赋值
    private final UserService userService;
    //构造函数
    public UserController(UserService userService) {
        this.userService = userService;
    }

    //注册求职者用户
    @OperationLog("用户注册")
    @PostMapping("/register")
    public Result<Void> userRegister(@RequestBody @Valid User user) {
        // 查询用户名是否已存在
        if (userService.findByUsername(user.getUsername()) != null) {
            return Result.error("用户名已存在");
        }
        // 注册
        userService.register(user.getUsername(), user.getPassword());
        return Result.success();
    }
    //用户登录
    @OperationLog("用户登录")
    @PostMapping("/login")
    public Result<String> loginUser(@RequestBody @Valid User user) {

        // 查询用户
        User userlogin = userService.findByUsername(user.getUsername());
        // 用户不存在
        if (userlogin == null) {
            return Result.error("用户不存在，请先注册");
        }
        // 验证密码
        String encryptPassword = Md5Util.encrypt(user.getPassword());
        // 密码错误
        if (!encryptPassword.equals(userlogin.getPassword())) {
            return Result.error("用户名或密码错误");
        }
        // 登录成功
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", userlogin.getId());
        claims.put("username", userlogin.getUsername());
        String token = JwtUtil.generateToken(claims);
        return Result.success(token);
    }
    //显示当前用户信息
    @GetMapping("/info")
    public Result<User> infoUser() {
        Long id = ThreadLocalUtil.getUserId();
        // 根据用户ID查询用户信息
        User userinfo = userService.findById(id);
        if (userinfo != null) {
            return Result.success(userinfo);
        } else {
            return Result.error("用户不存在");
        }
    }
    // 修改当前用户资料
    @OperationLog("修改用户资料")
    @PutMapping("/info")
    public Result<Void> updateUser(@RequestBody @Valid UserUpdateDTO dto)
    {
        Long id = ThreadLocalUtil.getUserId();

        // DTO 转 Entity
        User user = new User();

        user.setId(id);
        user.setUsername(dto.getUsername());
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());

        // 修改用户资料
        userService.update(user);

        return Result.success();
    }
    //修改密码
    @OperationLog("修改用户密码")
    @PatchMapping("/password")
    public Result<Void> updatePassword(@RequestBody @Valid UserPasswordDTO dto){
        Long id = ThreadLocalUtil.getUserId();
        //判断两次密码
        if(!dto.getNewPassword().equals(dto.getRePassword())){
            return Result.error("两次密码不一致");
        }
        userService.updatePassword(id,dto.getOldPassword(),dto.getNewPassword());
        return Result.success();
    }

}