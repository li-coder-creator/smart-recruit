package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.enums.JobSalaryUnit;
import com.recruit.smartrecruit.entity.enums.JobDirection;
import com.recruit.smartrecruit.entity.enums.RequiredAbilitiesSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JobListVO {
    private Long id;
    private String title;
    private Long companyId;
    private String companyName;
    /** 企业未上传 Logo 时返回 null，默认头像由前端展示。 */
    private String companyLogo;
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
    private String summary;
    private String benefits;
    /** 展示文本：结构化来源为 rawText 按换行连接；旧文本保持原样。 */
    private String requiredSkills;
    private String preferredSkills;
    private JobDirection directionCode;
    private RequiredAbilitiesSource requiredAbilitiesSource;
    private Long requiredAbilitiesVersion;
    private LocalDateTime publishedAt;
    private LocalDate deadline;
    private LocalDateTime updateTime;
}
