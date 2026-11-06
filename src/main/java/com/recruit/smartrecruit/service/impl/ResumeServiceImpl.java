package com.recruit.smartrecruit.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.recruit.smartrecruit.common.PageResult;
import com.recruit.smartrecruit.constant.ResumeConstants;
import com.recruit.smartrecruit.dto.*;
import com.recruit.smartrecruit.entity.*;
import com.recruit.smartrecruit.entity.enums.ApplicationStatus;
import com.recruit.smartrecruit.entity.enums.ResumeListFilter;
import com.recruit.smartrecruit.entity.enums.ExperienceType;
import com.recruit.smartrecruit.entity.enums.FormalExperienceStatus;
import com.recruit.smartrecruit.entity.enums.JobDirection;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.ResumeMapper;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.service.ResumeService;
import com.recruit.smartrecruit.vo.ResumeDetailVO;
import com.recruit.smartrecruit.vo.ResumeListVO;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


import java.net.URI;
import java.time.LocalDate;
import java.time.Clock;
import java.util.Objects;
import java.util.List;

@Service
public class ResumeServiceImpl implements ResumeService {
    private final ResumeMapper resumeMapper;
    private final PermissionService permissionService;
    private final Clock clock;

    public ResumeServiceImpl(ResumeMapper resumeMapper, PermissionService permissionService,
                             @Qualifier("applicationClock") Clock clock) {
        this.resumeMapper = resumeMapper;
        this.permissionService = permissionService;
        this.clock = clock;
    }
    //添加简历
    @Override
    @Transactional
    public Long add(ResumeProfileCreateDTO resumebasic, Long userId) {
        permissionService.requireJobSeeker(userId);
        //DTO 转换为数据库实体
        Resumebasic resume = new Resumebasic();
        resume.setTitle(resumebasic.getTitle());
        resume.setRealName(resumebasic.getRealName());
        resume.setGender(resumebasic.getGender());
        resume.setEmail(resumebasic.getEmail());
        resume.setPhone(resumebasic.getPhone());
        resume.setCurrentCity(resumebasic.getCurrentCity());
        resume.setPoliticsStatus(resumebasic.getPoliticsStatus());
        resume.setHeadline(resumebasic.getHeadline());
        resume.setSummary(resumebasic.getSummary());
        //处理出生日期
        if (resumebasic.getBirthday() != null) {
            resume.setBirthday(resumebasic.getBirthday());
        }
        resume.setCareerStage(resumebasic.getCareerStage());
        resume.setFormalExperienceStatus(resumebasic.getFormalExperienceStatus());
        resume.setUserId(userId);
        //设置只能由后端管理的初始值
        resume.setIsDefault(false);
        resume.setIsFavorite(ResumeConstants.NOT_FAVORITE);
        resume.setCompletionRate(ResumeConstants.INITIAL_COMPLETION_RATE);
        resumeMapper.add(resume);
        refreshCompletionRate(resume.getId(), userId);
        //返回 MyBatis 回填的自增主键
        return resume.getId();
    }
    //获取当前用户所有简历
    @Override
    public PageResult<ResumeListVO> findResumeList(Long userId, Integer pageNum, Integer pageSize, String keyword, ResumeListFilter filter ) {
        permissionService.requireJobSeeker(userId);
        //规范化关键字（去掉前后空格）
        String normalizedKeyword = null;

        if (keyword != null && !keyword.isBlank()) {
            normalizedKeyword = keyword.trim();
        }
        //创建投递状态集合
        List<Integer> applyingStatuses = List.of(
                ApplicationStatus.PENDING.getCode(),
                ApplicationStatus.SCREENING.getCode(),
                ApplicationStatus.INTERVIEW.getCode()
        );
        PageHelper.startPage(pageNum, pageSize);
        List<ResumeListVO> resumeListVOS = resumeMapper.findResumeList(userId,
                                                                       normalizedKeyword,
                                                                       filter,
                                                                       ResumeConstants.FAVORITE,
                                                                       ResumeConstants.COMPLETE_RATE,
                                                                       applyingStatuses);
        PageInfo<ResumeListVO> pageInfo = new PageInfo<>(resumeListVOS);
        return new PageResult<>(pageInfo.getTotal(), pageInfo.getList() );
    }
    //查询简历详情
    @Override
    public Resumebasic findById(Long id, Long userId) {
        permissionService.requireJobSeeker(userId);
        //从数据库拿到查询简历对应的实体
        Resumebasic resume=resumeMapper.findById(id);
        if (resume == null) {
            throw new BusinessException("简历不存在");
        }
        //判断查询简历是当前用户的简历
        if (!resume.getUserId().equals(userId)){
            throw new BusinessException("无权限访问");
        }
        return resume;
    }
    //修改简历
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void update(ResumeProfileUpdateDTO newResume,Long userId,Long resumeId) {
        Resumebasic resume = requireOwnedResumeForUpdate(resumeId, userId);
        if (newResume.wasProvided("careerStage")) {
            resume.setCareerStage(newResume.getCareerStage());
        }
        if (newResume.wasProvided("formalExperienceStatus")) {
            validateNoneDeclaration(newResume.getFormalExperienceStatus(), resumeMapper.getResumeExperience(resumeId));
            resume.setFormalExperienceStatus(newResume.getFormalExperienceStatus());
        }
        // 只修改允许前端修改的基础信息
        resume.setTitle(newResume.getTitle());
        resume.setRealName(newResume.getRealName());
        resume.setGender(newResume.getGender());
        resume.setEmail(newResume.getEmail());
        resume.setPhone(newResume.getPhone());
        resume.setCurrentCity(newResume.getCurrentCity());
        resume.setPoliticsStatus(newResume.getPoliticsStatus());
        resume.setHeadline(newResume.getHeadline());
        resume.setSummary(newResume.getSummary());
        resume.setBirthday(newResume.getBirthday());
        resumeMapper.update(resume);
        refreshCompletionRate(resumeId, userId);
    }
    //删除简历
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long id, Long userId) {
        requireOwnedResumeForUpdate(id, userId);
        //先级联删除简历关联的各子模块数据，避免产生孤儿数据
        resumeMapper.deleteJobPreferenceByResumeId(id);
        resumeMapper.deleteEducationByResumeId(id);
        resumeMapper.deleteExperienceByResumeId(id);
        resumeMapper.deleteProjectByResumeId(id);
        resumeMapper.deleteSkillByResumeId(id);
        resumeMapper.deleteCertByResumeId(id);
        resumeMapper.deleteLinkByResumeId(id);
        // 删除默认简历后允许当前用户暂时没有默认简历。
        resumeMapper.delete(id);
    }

    //添加求职意向
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void addJobPreference(Long resumeId, JobPreferenceSaveDTO dto, Long userId) {
        requireOwnedResumeForUpdate(resumeId, userId);
        if (resumeMapper.findJobPreferenceByResumeId(resumeId) != null) {
            throw new BusinessException("求职意向已存在");
        }
        JobPreference preference = new JobPreference();
        applyPreference(dto, preference);
        validateSalaryRange(preference.getSalaryMin(), preference.getSalaryMax());
        resumeMapper.addJobPreference(resumeId, preference);
        refreshCompletionRate(resumeId, userId);
    }
    //获取求职意向
    @Override
    public JobPreference getJobPreference(Long resumeId, Long userId) {
        findById(resumeId, userId);
        return resumeMapper.findJobPreferenceByResumeId(resumeId);
    }
    //修改求职意向
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateJobPreference(Long preferenceId, JobPreferenceSaveDTO dto, Long userId) {
        JobPreference lookup = resumeMapper.findJobPreferenceById(preferenceId);
        if (lookup == null) throw new BusinessException("求职意向不存在");
        requireOwnedResumeForUpdate(lookup.getResumeId(), userId);
        JobPreference current = resumeMapper.findJobPreferenceByIdForUpdate(preferenceId);
        if (current == null) throw new BusinessException("求职意向不存在");
        applyPreference(dto, current);
        validateSalaryRange(current.getSalaryMin(), current.getSalaryMax());
        resumeMapper.updateJobPreference(preferenceId, current);
        refreshCompletionRate(current.getResumeId(), userId);
    }
    //删除求职意向
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteJobPreference(Long preferenceId, Long userId) {
        JobPreference lookup = resumeMapper.findJobPreferenceById(preferenceId);
        if (lookup == null) throw new BusinessException("求职意向不存在");
        requireOwnedResumeForUpdate(lookup.getResumeId(), userId);
        JobPreference current = resumeMapper.findJobPreferenceByIdForUpdate(preferenceId);
        if (current == null) throw new BusinessException("求职意向不存在");
        resumeMapper.deleteJobPreference(preferenceId);
        refreshCompletionRate(current.getResumeId(), userId);
    }

    //添加简历教育经历
    @Override
    @Transactional
    public void addResumeEducation(Long resumeId, ResumeEducation resumeEducation, Long userId) {
        findById(resumeId, userId);
        //设置默认排序顺序
        if (resumeEducation.getSortOrder() == null) {
            resumeEducation.setSortOrder(ResumeConstants.DEFAULT_SORT_ORDER);
        }
        validateDateRange(resumeEducation.getStartDate(), resumeEducation.getEndDate(), "教育经历");
        resumeMapper.addResumeEducation(resumeId, resumeEducation);
        refreshCompletionRate(resumeId, userId);
    }
    //获取简历教育经历
    @Override
    public List<ResumeEducation> getResumeEducation(Long resumeId, Long userId) {
        findById(resumeId, userId);
        return resumeMapper.getResumeEducation(resumeId);
    }
    //修改简历教育经历
    @Override
    @Transactional
    public void updateResumeEducation(Long educationId, ResumeEducation resumeEducation, Long userId) {
        ResumeEducation resumeEducation1=resumeMapper.findEducationById(educationId);
        if (resumeEducation1 == null) {
            throw new BusinessException("教育经历不存在");
        }
        Long resumeId=resumeEducation1.getResumeId();
        findById(resumeId, userId);
        validateDateRange(resumeEducation.getStartDate(), resumeEducation.getEndDate(), "教育经历");
        resumeMapper.updateResumeEducation(educationId, resumeEducation);
        refreshCompletionRate(resumeId, userId);
    }
    //删除简历教育经历
    @Override
    @Transactional
    public void deleteResumeEducation( Long educationId, Long userId) {
        ResumeEducation exist = resumeMapper.findEducationById(educationId);
        if (exist == null) {
            throw new BusinessException("教育经历不存在");
        }
        findById(exist.getResumeId(), userId);
        resumeMapper.deleteResumeEducation(educationId);
        refreshCompletionRate(exist.getResumeId(), userId);
    }

    //添加简历工作经历
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void addResumeExperience(Long resumeId, ResumeExperienceSaveDTO dto, Long userId) {
        Resumebasic resume = requireOwnedResumeForUpdate(resumeId, userId);
        ResumeExperience experience = new ResumeExperience();
        experience.setExperienceType(ExperienceType.UNKNOWN);
        applyExperience(dto, experience);
        validateExperience(experience);
        resumeMapper.addResumeExperience(resumeId, experience);
        reconcileExperienceDeclaration(resume, false);
        refreshCompletionRate(resumeId, userId);
    }
    //获取简历工作经历
    @Override
    public List<ResumeExperience> getResumeExperience(Long resumeId, Long userId) {
        findById(resumeId, userId);
        return resumeMapper.getResumeExperience(resumeId);
    }
    //修改简历工作经历
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateResumeExperience(Long experienceId, ResumeExperienceSaveDTO dto, Long userId) {
        ResumeExperience lookup = resumeMapper.findExperienceById(experienceId);
        if (lookup == null) throw new BusinessException("工作经历不存在");
        Resumebasic resume = requireOwnedResumeForUpdate(lookup.getResumeId(), userId);
        ResumeExperience current = resumeMapper.findExperienceByIdForUpdate(experienceId);
        if (current == null) throw new BusinessException("工作经历不存在");
        boolean wasFormal = current.getExperienceType() == ExperienceType.FORMAL;
        applyExperience(dto, current);
        validateExperience(current);
        resumeMapper.updateResumeExperience(experienceId, current);
        reconcileExperienceDeclaration(resume, wasFormal && current.getExperienceType() != ExperienceType.FORMAL);
        refreshCompletionRate(current.getResumeId(), userId);
    }
    //删除简历工作经历
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteResumeExperience(Long experienceId, Long userId) {
        ResumeExperience lookup = resumeMapper.findExperienceById(experienceId);
        if (lookup == null) throw new BusinessException("工作经历不存在");
        Resumebasic resume = requireOwnedResumeForUpdate(lookup.getResumeId(), userId);
        ResumeExperience current = resumeMapper.findExperienceByIdForUpdate(experienceId);
        if (current == null) throw new BusinessException("工作经历不存在");
        resumeMapper.deleteResumeExperience(experienceId);
        reconcileExperienceDeclaration(resume, current.getExperienceType() == ExperienceType.FORMAL);
        refreshCompletionRate(current.getResumeId(), userId);
    }

    //添加简历项目经历
    @Override
    @Transactional
    public void addResumeProject(Long resumeId, ResumeProject project, Long userId) {
        findById(resumeId, userId);
        //设置默认排序顺序
        if (project.getSortOrder() == null) {
            project.setSortOrder(ResumeConstants.DEFAULT_SORT_ORDER);
        }
        validateDateRange(project.getStartDate(), project.getEndDate(), "项目经历");
        resumeMapper.addResumeProject(resumeId, project);
        refreshCompletionRate(resumeId, userId);
    }
    //获取简历项目经历
    @Override
    public List<ResumeProject> getResumeProject(Long resumeId, Long userId) {
        findById(resumeId, userId);
        return resumeMapper.getResumeProject(resumeId);
    }
    //修改简历项目经历
    @Override
    @Transactional
    public void updateResumeProject(Long projectId, ResumeProject project, Long userId) {
        ResumeProject exist = resumeMapper.findProjectById(projectId);
        if (exist == null) {
            throw new BusinessException("项目经历不存在");
        }
        findById(exist.getResumeId(), userId);
        if (project.getSortOrder() == null) {
            project.setSortOrder(exist.getSortOrder());
        }
        validateDateRange(project.getStartDate(), project.getEndDate(), "项目经历");
        resumeMapper.updateResumeProject(projectId, project);
        refreshCompletionRate(exist.getResumeId(), userId);

    }
    //删除简历项目经历
    @Override
    @Transactional
    public void deleteResumeProject(Long projectId, Long userId) {
        ResumeProject exist = resumeMapper.findProjectById(projectId);
        if (exist == null) {
            throw new BusinessException("项目经历不存在");
        }
        findById(exist.getResumeId(), userId);
        resumeMapper.deleteResumeProject(projectId);
        refreshCompletionRate(exist.getResumeId(), userId);

    }
    //技能
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void addResumeSkill(Long resumeId, ResumeSkillSaveDTO dto, Long userId) {
        requireOwnedResumeForUpdate(resumeId, userId);
        ResumeSkill skill = new ResumeSkill();
        applySkill(dto, skill);
        resumeMapper.addResumeSkill(resumeId, skill);
        refreshCompletionRate(resumeId, userId);
    }
    @Override
    public List<ResumeSkill> getResumeSkill(Long resumeId, Long userId) {
        findById(resumeId, userId);
        return resumeMapper.getResumeSkill(resumeId);
    }
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateResumeSkill(Long skillId, ResumeSkillSaveDTO dto, Long userId) {
        ResumeSkill lookup = resumeMapper.findSkillById(skillId);
        if (lookup == null) throw new BusinessException("技能不存在");
        requireOwnedResumeForUpdate(lookup.getResumeId(), userId);
        ResumeSkill current = resumeMapper.findSkillByIdForUpdate(skillId);
        if (current == null) throw new BusinessException("技能不存在");
        applySkill(dto, current);
        resumeMapper.updateResumeSkill(skillId, current);
        refreshCompletionRate(current.getResumeId(), userId);
    }
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteResumeSkill(Long skillId, Long userId) {
        ResumeSkill lookup = resumeMapper.findSkillById(skillId);
        if (lookup == null) throw new BusinessException("技能不存在");
        requireOwnedResumeForUpdate(lookup.getResumeId(), userId);
        ResumeSkill current = resumeMapper.findSkillByIdForUpdate(skillId);
        if (current == null) throw new BusinessException("技能不存在");
        resumeMapper.deleteResumeSkill(skillId);
        refreshCompletionRate(current.getResumeId(), userId);
    }

    //证书
    @Override
    public void addResumeCert(Long resumeId, ResumeCert cert, Long userId) {
        findById(resumeId, userId);
        if(cert.getSortOrder() == null ){
            cert.setSortOrder(ResumeConstants.DEFAULT_SORT_ORDER);
        }
        resumeMapper.addResumeCert(resumeId, cert);
    }
    @Override
    public List<ResumeCert> getResumeCert(Long resumeId, Long userId) {
        findById(resumeId, userId);
        return resumeMapper.getResumeCert(resumeId);
    }
    @Override
    public void updateResumeCert(Long certId, ResumeCert cert, Long userId) {
        ResumeCert exist = resumeMapper.findCertById(certId);
        if (exist == null) {
            throw new BusinessException("证书不存在");
        }
        findById(exist.getResumeId(), userId);
        if (cert.getSortOrder() == null) {
            cert.setSortOrder(exist.getSortOrder());
        }
        resumeMapper.updateResumeCert(certId, cert);
    }
    @Override
    public void deleteResumeCert(Long certId, Long userId) {
        ResumeCert exist = resumeMapper.findCertById(certId);
        if (exist == null) {
            throw new BusinessException("证书不存在");
        }
        findById(exist.getResumeId(), userId);
        resumeMapper.deleteResumeCert(certId);
    }

    //作品链接
    @Override
    public void addResumeLink(Long resumeId, ResumeLink link, Long userId) {
        findById(resumeId, userId);
        if(link.getSortOrder() == null ){
            link.setSortOrder(ResumeConstants.DEFAULT_SORT_ORDER);
        }
        validateLinkUrl(link.getUrl());
        resumeMapper.addResumeLink(resumeId, link);
    }
    @Override
    public List<ResumeLink> getResumeLink(Long resumeId, Long userId) {
        findById(resumeId, userId);
        return resumeMapper.getResumeLink(resumeId);
    }
    @Override
    public void updateResumeLink(Long linkId, ResumeLink link, Long userId) {
        ResumeLink exist = resumeMapper.findLinkById(linkId);
        if (exist == null) {
            throw new BusinessException("作品链接不存在");
        }
        findById(exist.getResumeId(), userId);
        if (link.getSortOrder() == null) {
            link.setSortOrder(exist.getSortOrder());
        }
        validateLinkUrl(link.getUrl());
        resumeMapper.updateResumeLink(linkId, link);
    }
    @Override
    public void deleteResumeLink(Long linkId, Long userId) {
        ResumeLink exist = resumeMapper.findLinkById(linkId);
        if (exist == null) {
            throw new BusinessException("作品链接不存在");
        }
        findById(exist.getResumeId(), userId);
        resumeMapper.deleteResumeLink(linkId);

    }

    //简历全部详情
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResumeDetailVO getResumeDetail(Long resumeId, Long userId) {
        //校验简历存在且归属当前用户，同时拿到简历基本信息
        Resumebasic resume = findById(resumeId, userId);
        //分别查询各模块，避免多表 JOIN 聚合产生笛卡尔积重复
        ResumeDetailVO detail = new ResumeDetailVO();
        detail.setResume(resume);
        detail.setJobPreference(resumeMapper.findJobPreferenceByResumeId(resumeId));
        detail.setEducations(resumeMapper.getResumeEducation(resumeId));
        detail.setExperiences(resumeMapper.getResumeExperience(resumeId));
        detail.setProjects(resumeMapper.getResumeProject(resumeId));
        detail.setSkills(resumeMapper.getResumeSkill(resumeId));
        detail.setCerts(resumeMapper.getResumeCert(resumeId));
        detail.setLinks(resumeMapper.getResumeLink(resumeId));
        return detail;
    }

    //设置默认简历
    @Override
    @Transactional
    public void setDefaultResume(Long resumeId, Long userId) {
        // 同一用户的“设置默认”操作按顺序执行
        resumeMapper.lockUserForDefault(userId);

        Resumebasic resume=findById(resumeId, userId);
        //幂等处理
        if (Boolean.TRUE.equals(resume.getIsDefault())) {
            return;
        }

        resumeMapper.clearDefaultResume(
                userId,
                ResumeConstants.NOT_DEFAULT,
                ResumeConstants.DEFAULT
        );

        int updated = resumeMapper.setDefaultResume(
                resumeId,
                userId,
                ResumeConstants.DEFAULT
        );

        if (updated != 1) {
            throw new BusinessException("设置默认简历失败");
        }
    }

    //收藏简历
    @Override
    public void updateFavorite(Long resumeId, Long userId,Boolean favorite) {
        Resumebasic resume = findById(resumeId, userId);
        //已经是目标状态时直接返回，保证接口幂等
        if (Boolean.valueOf(resume.getIsFavorite()).equals(favorite)) {
            return;
        }
        int affectedRows = resumeMapper.updateFavorite(resumeId, userId, favorite);

        if (affectedRows != 1) {
            throw new BusinessException("收藏状态更新失败");
        }

    }
    //计算简历完成度
    private int calculateCompletionRate(ResumeDetailVO detail){
        Resumebasic resume = detail.getResume();
        JobPreference jobPreference = detail.getJobPreference();
        List<ResumeEducation> educations = detail.getEducations();
        List<ResumeExperience> experiences = detail.getExperiences();
        List<ResumeProject> projects = detail.getProjects();
        List<ResumeSkill> skills = detail.getSkills();

        int completionRate = ResumeConstants.INITIAL_COMPLETION_RATE;
        if (StringUtils.hasText(resume.getRealName())) completionRate += ResumeConstants.COMPLETION_ITEM_SCORE;
        if (StringUtils.hasText(resume.getEmail())) completionRate += ResumeConstants.COMPLETION_ITEM_SCORE;
        if (StringUtils.hasText(resume.getPhone())) completionRate += ResumeConstants.COMPLETION_ITEM_SCORE;
        if (StringUtils.hasText(resume.getCurrentCity())) completionRate += ResumeConstants.COMPLETION_ITEM_SCORE;
        if (jobPreference != null && StringUtils.hasText(jobPreference.getExpectedPosition())) completionRate += ResumeConstants.COMPLETION_ITEM_SCORE;
        if (jobPreference != null && StringUtils.hasText(jobPreference.getExpectedCity())) completionRate += ResumeConstants.COMPLETION_ITEM_SCORE;
        if (hasValidEducation(educations)) completionRate += ResumeConstants.COMPLETION_ITEM_SCORE;
        if (hasValidExperience(experiences) || hasValidProject(projects)) completionRate += ResumeConstants.COMPLETION_ITEM_SCORE;
        if (hasValidSkill(skills)) completionRate += ResumeConstants.COMPLETION_ITEM_SCORE;
        if (StringUtils.hasText(resume.getSummary())) completionRate += ResumeConstants.COMPLETION_ITEM_SCORE;

        return Math.min(completionRate, ResumeConstants.COMPLETE_RATE);
    }

    private Resumebasic requireOwnedResumeForUpdate(Long id, Long userId) {
        permissionService.requireJobSeeker(userId);
        Resumebasic resume = resumeMapper.findOwnedByIdForUpdate(id, userId);
        if (resume == null) throw new BusinessException("简历不存在或无权访问");
        return resume;
    }

    private void validateNoneDeclaration(FormalExperienceStatus status, List<ResumeExperience> experiences) {
        if (status == FormalExperienceStatus.NONE && experiences.stream().anyMatch(item ->
                item.getExperienceType() == ExperienceType.FORMAL || item.getExperienceType() == ExperienceType.UNKNOWN
                        || item.getExperienceType() == null)) {
            throw new BusinessException("存在正式或未分类经历，不能确认暂无正式工作经历");
        }
    }

    private void reconcileExperienceDeclaration(Resumebasic resume, boolean removedFormal) {
        List<ResumeExperience> experiences = resumeMapper.getResumeExperience(resume.getId());
        validateNoneDeclaration(resume.getFormalExperienceStatus(), experiences);
        boolean hasFormal = experiences.stream().anyMatch(item -> item.getExperienceType() == ExperienceType.FORMAL);
        FormalExperienceStatus status = resume.getFormalExperienceStatus();
        if (hasFormal && status == FormalExperienceStatus.UNKNOWN) status = FormalExperienceStatus.HAS_EXPERIENCE;
        if (removedFormal && !hasFormal && status == FormalExperienceStatus.HAS_EXPERIENCE) status = FormalExperienceStatus.UNKNOWN;
        if (status != resume.getFormalExperienceStatus()) {
            if (resumeMapper.updateFormalExperienceStatus(resume.getId(), resume.getUserId(), status) != 1) {
                throw new BusinessException("正式经历确认更新失败");
            }
            resume.setFormalExperienceStatus(status);
        }
    }

    private void applyExperience(ResumeExperienceSaveDTO dto, ResumeExperience target) {
        target.setCompanyName(dto.getCompanyName());
        target.setPosition(dto.getPosition());
        target.setPeriodText(dto.getPeriodText());
        target.setDescription(dto.getDescription());
        target.setAchievement(dto.getAchievement());
        target.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder()
                : target.getSortOrder() != null ? target.getSortOrder() : ResumeConstants.DEFAULT_SORT_ORDER);
        if (dto.wasProvided("startDate")) target.setStartDate(dto.getStartDate());
        if (dto.wasProvided("endDate")) target.setEndDate(dto.getEndDate());
        if (dto.wasProvided("experienceType")) target.setExperienceType(dto.getExperienceType());
        if (dto.wasProvided("isCurrent")) target.setIsCurrent(dto.getIsCurrent());
    }

    private void validateExperience(ResumeExperience experience) {
        LocalDate start = experience.getStartDate(), end = experience.getEndDate();
        validateDateRange(start, end, "工作经历");
        if (Boolean.TRUE.equals(experience.getIsCurrent()) && end != null) {
            throw new BusinessException("至今经历不能同时填写结束日期");
        }
        if (experience.getExperienceType() == ExperienceType.UNKNOWN) return;
        LocalDate today = LocalDate.now(clock);
        if (start == null || start.isAfter(today)) throw new BusinessException("已分类经历须填写不晚于今天的开始日期");
        if (experience.getIsCurrent() == null) throw new BusinessException("已分类经历须明确是否至今");
        if (Boolean.FALSE.equals(experience.getIsCurrent()) && (end == null || end.isAfter(today))) {
            throw new BusinessException("已结束经历须填写不晚于今天的结束日期");
        }
    }

    private void applySkill(ResumeSkillSaveDTO dto, ResumeSkill target) {
        target.setSkillName(dto.getSkillName());
        target.setDescription(dto.getDescription());
        target.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder()
                : target.getSortOrder() != null ? target.getSortOrder() : ResumeConstants.DEFAULT_SORT_ORDER);
        if (dto.wasProvided("proficiency")) target.setProficiency(dto.getProficiency());
        if (target.getProficiency() != null && target.getSkillName().strip().matches(
                com.recruit.smartrecruit.constant.ResumeValidationConstants.NEGATED_ABILITY_NAME_PATTERN)) {
            throw new BusinessException("技能否定描述与掌握程度矛盾，请确认后保存");
        }
    }

    private void applyPreference(JobPreferenceSaveDTO dto, JobPreference target) {
        JobDirection direction = dto.wasProvided("directionCode") ? dto.getDirectionCode()
                : Objects.equals(target.getExpectedPosition(), dto.getExpectedPosition()) ? target.getDirectionCode() : null;
        JobDirection exact = "后端开发".equals(dto.getExpectedPosition()) ? JobDirection.BACKEND
                : "前端开发".equals(dto.getExpectedPosition()) ? JobDirection.FRONTEND : null;
        if (direction != null && exact != null && direction != exact) {
            throw new BusinessException("期望职位与方向代码矛盾");
        }
        target.setExpectedPosition(dto.getExpectedPosition());
        target.setExpectedCity(dto.getExpectedCity());
        target.setSalaryMin(dto.getSalaryMin());
        target.setSalaryMax(dto.getSalaryMax());
        target.setSalaryText(dto.getSalaryText());
        target.setAvailableDate(dto.getAvailableDate());
        target.setDescription(dto.getDescription());
        target.setDirectionCode(direction);
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate, String sectionName) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(sectionName + "开始日期不能晚于结束日期");
        }
    }

    private void validateSalaryRange(Integer salaryMin, Integer salaryMax) {
        if (salaryMin != null && salaryMax != null && salaryMin > salaryMax) {
            throw new BusinessException("期望薪资最低值不能大于最高值");
        }
    }

    private void validateLinkUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!uri.isAbsolute()
                    || uri.getHost() == null
                    || !("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new BusinessException("作品链接必须是有效的HTTP或HTTPS地址");
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("作品链接格式不正确");
        }
    }

    private boolean hasValidEducation(List<ResumeEducation> educations) {
        return educations != null && educations.stream().anyMatch(education ->
                StringUtils.hasText(education.getSchoolName())
                        && StringUtils.hasText(education.getDegree())
                        && StringUtils.hasText(education.getMajor()));
    }

    private boolean hasValidExperience(List<ResumeExperience> experiences) {
        return experiences != null && experiences.stream().anyMatch(experience ->
                StringUtils.hasText(experience.getCompanyName())
                        && StringUtils.hasText(experience.getPosition())
                        && StringUtils.hasText(experience.getDescription()));
    }

    private boolean hasValidProject(List<ResumeProject> projects) {
        return projects != null && projects.stream().anyMatch(project ->
                StringUtils.hasText(project.getProjectName())
                        && StringUtils.hasText(project.getRole())
                        && StringUtils.hasText(project.getDescription()));
    }

    private boolean hasValidSkill(List<ResumeSkill> skills) {
        return skills != null && skills.stream().anyMatch(skill -> StringUtils.hasText(skill.getSkillName()));
    }

    private void refreshCompletionRate(Long resumeId, Long userId) {
        ResumeDetailVO detail = getResumeDetail(resumeId, userId);
        int completionRate = calculateCompletionRate(detail);
        Integer currentCompletionRate = detail.getResume().getCompletionRate();
        if (Integer.valueOf(completionRate).equals(currentCompletionRate)) {
            return;
        }
        int updated = resumeMapper.updateCompletionRate(resumeId, userId, completionRate);
        if (updated != 1) {
            throw new BusinessException("简历完成度更新失败");
        }
    }

    @Override
    public Integer getCompletionRate(Long resumeId, Long userId) {
        return findById(resumeId, userId).getCompletionRate();
    }
}

