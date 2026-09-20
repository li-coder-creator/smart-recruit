package com.recruit.smartrecruit.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeListVO {
    private Long id;
    private String title;
    private String realName;
    private String headline;
    private String expectedPosition;
    private String schoolName;
    private String degree;
    private String currentCity;
    private Boolean applying;
    // 是否默认简历
    private Boolean isDefault;
    // 是否收藏
    private Boolean isFavorite;
    // 简历完成度 0-100
    private Integer completionRate;
    private LocalDateTime updateTime;
}
