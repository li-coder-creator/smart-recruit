package com.recruit.smartrecruit.entity;

import java.time.LocalDateTime;
//lomok 在编译阶段，为实体自动生成getter、setter、toString、equals、hashCode方法
//pom.xml 中添加 lombok 插件，在实体类中添加 @Data 注解
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.Email;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    //参数校验
    private Long id;
    @NotBlank(message = "用户名不能为空")
    @Pattern(
            regexp = "^[a-zA-Z0-9_]{5,16}$",
            message = "用户名必须5~16位，只能包含数字字母下划线"
    )
    private String username;
    @NotBlank(message = "密码不能为空")
    @Pattern(
            regexp = "^[a-zA-Z0-9_]{5,16}$",
            message = "密码名必须5~16位，只能包含数字字母下划线"
    )
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)//JSON 忽略字段
    private String password;
    private String nickname;
    @Email(message = "邮箱格式不正确")
    private String email;
    @Pattern(
            regexp = "^1[3-9]\\d{9}$",
            message = "手机号格式不正确"
    )
    private String phone;
    private LocalDateTime createTime;


}