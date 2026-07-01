package com.recruit.smartrecruit.mapper;

import com.recruit.smartrecruit.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMapper {
    // 用户名查询
    @Select("""
            SELECT *
            FROM sys_user
            WHERE username = #{username}
            """)
    User findByUsername(String username);

    // 注册添加
    @Insert("INSERT INTO sys_user (username, password) VALUES (#{username}, #{password})")
    void add(String username, String password);
    //显示当前用户信息
    @Select("""
            SELECT
                id,
                username,
                password,
                nickname,
                email,
                phone,
                create_time 
            FROM sys_user
            WHERE id = #{id}
            """)
    User findById(Long id);
    //修改当前用户
    @Update("""
            UPDATE sys_user
            SET
                nickname=#{nickname},
                email=#{email},
                phone=#{phone}
            WHERE id=#{id}
            """
    )
    void update(User user);
    //修改密码
    @Update("""
            UPDATE sys_user
            SET
            password=#{password}    
            WHERE id=#{id}
            """
    )
    void updatePassword(
            @Param("id") Long id,
            @Param("password") String password
    );

}