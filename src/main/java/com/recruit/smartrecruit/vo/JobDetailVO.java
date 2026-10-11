package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.Job;
import com.recruit.smartrecruit.entity.enums.JobSalaryUnit;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class JobDetailVO {
    private Long id;
    private Long companyId;
    private String title;
    private Integer status;
    private String category;
    private String team;
    private Integer headcount;
    private String employment;
    private String city;
    private String workMode;
    private String address;
    private Integer salaryMin;
    private Integer salaryMax;
    private JobSalaryUnit salaryUnit;
    private Boolean negotiable;
    private String summary;
    private String responsibilities;
    private String requirements;
    private String bonus;
    private String requiredSkills;
    private String preferredSkills;
    private String experience;
    private String education;
    private String benefits;
    private LocalDate deadline;
    private LocalDateTime publishedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String description; // 仅兼容旧岗位数据，不冒充新的职责或要求

    public JobDetailVO() {
    }

    public JobDetailVO(Job job) {
        this.id = job.getId();
        this.companyId = job.getCompanyId();
        this.title = job.getTitle();
        this.status = job.getStatus();
        this.category = job.getCategory();
        this.team = job.getTeam();
        this.headcount = job.getHeadcount();
        this.employment = job.getEmployment();
        this.city = job.getCity();
        this.workMode = job.getWorkMode();
        this.address = job.getAddress();
        this.salaryMin = job.getSalaryMin();
        this.salaryMax = job.getSalaryMax();
        this.salaryUnit = job.getSalaryUnit();
        this.negotiable = job.getNegotiable();
        this.summary = job.getSummary();
        this.responsibilities = job.getResponsibilities();
        this.requirements = job.getRequirements();
        this.bonus = job.getBonus();
        this.requiredSkills = job.getRequiredSkills();
        this.preferredSkills = job.getPreferredSkills();
        this.experience = job.getExperience();
        this.education = job.getEducation();
        this.benefits = job.getBenefits();
        this.deadline = job.getDeadline();
        this.publishedAt = job.getPublishedAt();
        this.endedAt = job.getEndedAt();
        this.createTime = job.getCreateTime();
        this.updateTime = job.getUpdateTime();
        this.description = job.getDescription();
    }
}
