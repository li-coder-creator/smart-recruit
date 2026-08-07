package com.recruit.smartrecruit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UserPasswordDTO {
    //接口需要什么，就定义什么。
    @NotBlank(message = "密码不能为空")
    private String oldPassword;
    @NotBlank(message = "密码不能为空")
    @Pattern(
            regexp = "^[a-zA-Z0-9_]{5,26}$",
            message = "密码名必须5~26位，只能包含数字字母下划线"
    )
    private String newPassword;
    @NotBlank(message = "密码不能为空")
    private String rePassword;
}
