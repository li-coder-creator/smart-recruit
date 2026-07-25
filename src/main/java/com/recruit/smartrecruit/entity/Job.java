package com.recruit.smartrecruit.entity;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Job {
    private Long id;
    private Long companyId;
    @NotBlank(message = "岗位名称不能为空")
    private String title;
    private String description;
    private Integer salaryMin;
    private Integer salaryMax;
    private String city;
    private String experience;
    private String education;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
