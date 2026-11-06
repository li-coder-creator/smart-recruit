package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 保持resume/experiences/skills等原有JSON层级，待新版读取入口接入。 */
@Data
@NoArgsConstructor
public class ResumeProfileDetailVO {
    private ResumeBasicVO resume;
    private JobPreferenceVO jobPreference;
    private List<ResumeEducation> educations;
    private List<ResumeExperienceVO> experiences;
    private List<ResumeProject> projects;
    private List<ResumeSkillVO> skills;
    private List<ResumeCert> certs;
    private List<ResumeLink> links;

    public ResumeProfileDetailVO(ResumeDetailVO source) {
        resume = source.getResume() == null ? null : new ResumeBasicVO(source.getResume());
        jobPreference = source.getJobPreference() == null ? null : new JobPreferenceVO(source.getJobPreference());
        educations = source.getEducations();
        experiences = source.getExperiences() == null ? null
                : source.getExperiences().stream().map(ResumeExperienceVO::new).toList();
        projects = source.getProjects();
        skills = source.getSkills() == null ? null
                : source.getSkills().stream().map(ResumeSkillVO::new).toList();
        certs = source.getCerts();
        links = source.getLinks();
    }
}
