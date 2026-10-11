package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.enums.JobSalaryUnit;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JobListVO {
    private Long id;
    private String title;
    private String category;
    private String team;
    private String employment;
    private Integer status;          // 0招聘中、1暂停、2结束、3草稿
    private String city;
    private String experience;
    private String education;
    private Integer salaryMin;
    private Integer salaryMax;
    private JobSalaryUnit salaryUnit;
    private Boolean negotiable;
    private LocalDateTime updateTime;
}