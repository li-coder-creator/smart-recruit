package com.recruit.smartrecruit.entity;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Company {
    private Long id;
    private Long userId;
    @NotBlank(message = "企业名称不能为空")
    private String name;
    private String description;
    private String logo;
    private String city;
    private String address;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
