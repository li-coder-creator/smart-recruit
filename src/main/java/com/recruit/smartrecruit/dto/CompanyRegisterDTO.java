package com.recruit.smartrecruit.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompanyRegisterDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "企业名称不能为空")
    private String companyName;

    private String description;

    private String logo;

    private String city;

    private String address;
}