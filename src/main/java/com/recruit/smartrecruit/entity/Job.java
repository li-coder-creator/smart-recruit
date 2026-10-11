package com.recruit.smartrecruit.entity;

import com.recruit.smartrecruit.entity.enums.JobSalaryUnit;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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
    /** 岗位类别 */
    private String category;
    /** 所属团队 */
    private String team;
    /** 招聘人数 */
    private Integer headcount;
    /** 用工类型 */
    private String employment;
    /** 办公方式 */
    private String workMode;
    /** 详细地址 */
    private String address;
    /** 薪资单位：MONTH/DAY/YEAR；旧薪资按月处理 */
    private JobSalaryUnit salaryUnit;
    /** 是否薪资面议 */
    private Boolean negotiable;
    /** 一句话介绍 */
    private String summary;
    /** 岗位职责 */
    private String responsibilities;
    /** 任职要求 */
    private String requirements;
    /** 加分项 */
    private String bonus;
    /** 必备技能展示文本 */
    private String requiredSkills;
    /** 加分技能展示文本 */
    private String preferredSkills;
    /** 福利亮点 */
    private String benefits;
    /** 计划截止日期 */
    private LocalDate deadline;
    /** 发布时间 */
    private LocalDateTime publishedAt;
    /** 结束时间 */
    private LocalDateTime endedAt;
    /** 0招聘中，1已暂停，2已结束，3草稿 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
