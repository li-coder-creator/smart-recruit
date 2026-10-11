package com.recruit.smartrecruit.dto;

import com.recruit.smartrecruit.constant.JobConstants;
import com.recruit.smartrecruit.constant.JobValidationConstants;
import com.recruit.smartrecruit.entity.Job;
import com.recruit.smartrecruit.entity.enums.JobSalaryUnit;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class JobSaveDTO {
    @NotBlank(message = "岗位名称不能为空")
    @Size(max = JobValidationConstants.TITLE_MAX_LENGTH, message = "岗位名称不能超过100个字符")
    private String title;
    /** 旧版岗位描述，保留用于兼容过渡。 */
    private String description;
    @Positive(message = "薪资下限必须为正数")
    private Integer salaryMin;
    @Positive(message = "薪资上限必须为正数")
    private Integer salaryMax;
    @Size(max = JobValidationConstants.CITY_MAX_LENGTH, message = "工作城市不能超过50个字符")
    private String city;
    @Size(max = JobValidationConstants.EXPERIENCE_MAX_LENGTH, message = "经验要求不能超过50个字符")
    private String experience;
    @Size(max = JobValidationConstants.EDUCATION_MAX_LENGTH, message = "学历要求不能超过50个字符")
    private String education;
    /** 岗位类别 */
    @Size(max = JobValidationConstants.CATEGORY_MAX_LENGTH, message = "岗位类别不能超过50个字符")
    private String category;
    /** 所属团队 */
    @Size(max = JobValidationConstants.TEAM_MAX_LENGTH, message = "所属团队不能超过100个字符")
    private String team;
    /** 招聘人数 */
    @Min(value = JobValidationConstants.HEADCOUNT_MIN, message = "招聘人数必须大于等于1")
    private Integer headcount;
    /** 用工类型 */
    @Size(max = JobValidationConstants.EMPLOYMENT_MAX_LENGTH, message = "用工类型不能超过20个字符")
    private String employment;
    /** 办公方式 */
    @Size(max = JobValidationConstants.WORK_MODE_MAX_LENGTH, message = "办公方式不能超过20个字符")
    private String workMode;
    /** 详细地址 */
    @Size(max = JobValidationConstants.ADDRESS_MAX_LENGTH, message = "详细地址不能超过255个字符")
    private String address;
    /** 薪资单位：MONTH/DAY/YEAR；旧薪资按月处理 */
    private JobSalaryUnit salaryUnit;
    /** 是否薪资面议 */
    private Boolean negotiable;
    /** 一句话介绍 */
    @Size(max = JobValidationConstants.SUMMARY_MAX_LENGTH, message = "一句话介绍不能超过255个字符")
    private String summary;
    /** 岗位职责 */
    private String responsibilities;
    /** 任职要求 */
    private String requirements;
    /** 加分项 */
    private String bonus;
    /** 必备技能展示文本 */
    @Size(max = JobValidationConstants.SKILLS_MAX_LENGTH, message = "必备技能展示文本不能超过500个字符")
    private String requiredSkills;
    /** 加分技能展示文本 */
    @Size(max = JobValidationConstants.SKILLS_MAX_LENGTH, message = "加分技能展示文本不能超过500个字符")
    private String preferredSkills;
    /** 福利亮点 */
    @Size(max = JobValidationConstants.BENEFITS_MAX_LENGTH, message = "福利亮点不能超过255个字符")
    private String benefits;
    /** 计划截止日期 */
    private LocalDate deadline;

    /**
     * 将 DTO 字段应用到目标岗位实体上。
     * 新增时传入空 Job，更新时传入已从库中查出的 Job（保留 id/companyId/status 等字段）。
     */
    public void applyTo(Job job) {
        job.setTitle(this.title);
        job.setDescription(this.description);
        job.setSalaryMin(this.salaryMin);
        job.setSalaryMax(this.salaryMax);
        job.setCity(this.city);
        job.setExperience(this.experience);
        job.setEducation(this.education);
        job.setCategory(this.category);
        job.setTeam(this.team);
        job.setHeadcount(this.headcount != null ? this.headcount : JobConstants.DEFAULT_HEADCOUNT);
        job.setEmployment(this.employment);
        job.setWorkMode(this.workMode);
        job.setAddress(this.address);
        job.setSalaryUnit(this.salaryUnit != null ? this.salaryUnit : JobConstants.DEFAULT_SALARY_UNIT);
        job.setNegotiable(this.negotiable != null ? this.negotiable : JobConstants.DEFAULT_NEGOTIABLE);
        job.setSummary(this.summary);
        job.setResponsibilities(this.responsibilities);
        job.setRequirements(this.requirements);
        job.setBonus(this.bonus);
        job.setRequiredSkills(this.requiredSkills);
        job.setPreferredSkills(this.preferredSkills);
        job.setBenefits(this.benefits);
        job.setDeadline(this.deadline);
    }
}
