package com.recruit.smartrecruit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CompanyRegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Pattern(
            regexp = "^[a-zA-Z0-9_]{5,26}$",
            message = "用户名必须5~26位，只能包含数字字母下划线"
    )
    private String username;
    @NotBlank(message = "密码不能为空")
    @Pattern(
            regexp = "^[a-zA-Z0-9_]{5,26}$",
            message = "密码名必须5~26位，只能包含数字字母下划线"
    )
    private String Password;
    @NotBlank(message = "企业名称不能为空")
    private String companyName;

    private String description;

    private String logo;

    private String city;

    private String address;
}