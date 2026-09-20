package com.recruit.smartrecruit.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.LocalDateTime;
@Data              // get、set、toString、equals、hashCode
@NoArgsConstructor // 无参构造（ORM框架必需）
@AllArgsConstructor// 全部字段构造，方便new对象快速赋值
public class Resumebasic {
    private Long id;

    private Long userId;
    @NotBlank(message = "简历名称不能为空")
    private String title;

    private String description;

    private String fileUrl;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String realName;

    private String gender;

    private LocalDate birthday;

    private String email;
    // 电话
    private String phone;

    private String currentCity;
    //政治面貌
    private String politicsStatus;

    private String headline;

    private String summary;
    // 是否默认简历
    private Boolean isDefault;
    // 是否收藏
    private Boolean isFavorite;
    // 简历完成度 0-100
    private Integer completionRate;

}
