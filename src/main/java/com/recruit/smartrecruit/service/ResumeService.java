package com.recruit.smartrecruit.service;

import com.recruit.smartrecruit.common.PageResult;
import com.recruit.smartrecruit.dto.ResumeBasicUpdateDTO;
import com.recruit.smartrecruit.dto.ResumeCreateDTO;
import com.recruit.smartrecruit.entity.*;
import com.recruit.smartrecruit.entity.enums.ResumeListFilter;
import com.recruit.smartrecruit.vo.ResumeDetailVO;
import com.recruit.smartrecruit.vo.ResumeListVO;
import jakarta.validation.Valid;

import java.util.List;

public interface ResumeService {
    //添加简历
    Long add( ResumeCreateDTO resumebasic, Long userId);
    //获取当前用户所有简历
    PageResult<ResumeListVO> findResumeList(Long userId, Integer page, Integer size, String keyword, ResumeListFilter filter);
    //查询简历详情
    Resumebasic findById(Long id, Long userId);
    //修改简历
    void update(ResumeBasicUpdateDTO newResume,Long userId,Long resumeId);
    //删除简历
    void delete(Long id,Long userId);

    //求职意向
    void addJobPreference(Long resumeId, @Valid JobPreference jobPreference, Long userId);
    JobPreference getJobPreference(Long resumeId, Long userId);
    void updateJobPreference(Long preferenceId, @Valid JobPreference jobPreference, Long userId);
    void deleteJobPreference(Long preferenceId, Long userId);

    //教育经历
    void addResumeEducation(Long resumeId, @Valid ResumeEducation education, Long userId);
    List<ResumeEducation> getResumeEducation(Long resumeId, Long userId);
    void updateResumeEducation(Long educationId, @Valid ResumeEducation education, Long userId);
    void deleteResumeEducation(Long educationId, Long userId);

    //工作经历
    void addResumeExperience(Long resumeId, @Valid ResumeExperience experience, Long userId);
    List<ResumeExperience> getResumeExperience(Long resumeId, Long userId);
    void updateResumeExperience(Long experienceId, @Valid ResumeExperience experience, Long userId);
    void deleteResumeExperience(Long experienceId, Long userId);

    //项目经历
    void addResumeProject(Long resumeId, @Valid ResumeProject project, Long userId);
    List<ResumeProject> getResumeProject(Long resumeId, Long userId);
    void updateResumeProject(Long projectId, @Valid ResumeProject project, Long userId);
    void deleteResumeProject(Long projectId, Long userId);

    //技能
    void addResumeSkill(Long resumeId, @Valid ResumeSkill skill, Long userId);
    List<ResumeSkill> getResumeSkill(Long resumeId, Long userId);
    void updateResumeSkill(Long skillId, @Valid ResumeSkill skill, Long userId);
    void deleteResumeSkill(Long skillId, Long userId);

    //证书
    void addResumeCert(Long resumeId, @Valid ResumeCert cert, Long userId);
    List<ResumeCert> getResumeCert(Long resumeId, Long userId);
    void updateResumeCert(Long certId, @Valid ResumeCert cert, Long userId);
    void deleteResumeCert(Long certId, Long userId);

    //作品链接
    void addResumeLink(Long resumeId, @Valid ResumeLink link, Long userId);
    List<ResumeLink> getResumeLink(Long resumeId, Long userId);
    void updateResumeLink(Long linkId, @Valid ResumeLink link, Long userId);
    void deleteResumeLink(Long linkId, Long userId);

    //简历全部详情
    ResumeDetailVO getResumeDetail(Long resumeId, Long userId);

    //设置默认简历
    void setDefaultResume(Long resumeId, Long userId);

    //收藏简历
    void updateFavorite(Long resumeId, Long userId,Boolean favorite);

    //查询简历完成度
    Integer getCompletionRate(Long resumeId, Long userId);
}
