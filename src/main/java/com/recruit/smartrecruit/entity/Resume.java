package com.recruit.smartrecruit.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
@Data              // get、set、toString、equals、hashCode
@NoArgsConstructor // 无参构造（ORM框架必需）
@AllArgsConstructor// 全部字段构造，方便new对象快速赋值
public class Resume {
    private Long id;

    private Long userId;
    @NotBlank(message = "简历名称不能为空")
    private String title;

    private String description;

    private String fileUrl;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
