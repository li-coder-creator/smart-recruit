package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.JobPreference;
import com.recruit.smartrecruit.entity.Resumebasic;
import com.recruit.smartrecruit.entity.ResumeCert;
import com.recruit.smartrecruit.entity.ResumeEducation;
import com.recruit.smartrecruit.entity.ResumeExperience;
import com.recruit.smartrecruit.entity.ResumeLink;
import com.recruit.smartrecruit.entity.ResumeProject;
import com.recruit.smartrecruit.entity.ResumeSkill;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

//简历全部详情
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResumeDetailVO {
    //简历基本信息
    private Resumebasic resume;
    //求职意向
    private JobPreference jobPreference;
    //教育经历
    private List<ResumeEducation> educations;
    //工作经历
    private List<ResumeExperience> experiences;
    //项目经历
    private List<ResumeProject> projects;
    //技能
    private List<ResumeSkill> skills;
    //证书
    private List<ResumeCert> certs;
    //作品链接
    private List<ResumeLink> links;
}
