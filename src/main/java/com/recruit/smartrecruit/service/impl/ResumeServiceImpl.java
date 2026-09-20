package com.recruit.smartrecruit.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.recruit.smartrecruit.common.PageResult;
import com.recruit.smartrecruit.constant.ResumeConstants;
import com.recruit.smartrecruit.dto.ResumeBasicUpdateDTO;
import com.recruit.smartrecruit.dto.ResumeCreateDTO;
import com.recruit.smartrecruit.entity.*;
import com.recruit.smartrecruit.entity.enums.ApplicationStatus;
import com.recruit.smartrecruit.entity.enums.ResumeListFilter;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.ResumeMapper;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.service.ResumeService;
import com.recruit.smartrecruit.vo.ResumeDetailVO;
import com.recruit.smartrecruit.vo.ResumeListVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@Service
public class ResumeServiceImpl implements ResumeService {
    private final ResumeMapper resumeMapper;
    private final PermissionService permissionService;
    public ResumeServiceImpl(ResumeMapper resumeMapper, PermissionService permissionService) {
        this.resumeMapper = resumeMapper;
        this.permissionService = permissionService;
    }
    //添加简历
    @Override
    @Transactional
    public Long add(ResumeCreateDTO resumebasic, Long userId) {
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
    @Transactional
    public void update(ResumeBasicUpdateDTO newResume,Long userId,Long resumeId) {
        Resumebasic resume = findById(resumeId, userId);
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
    public void delete(Long id,Long userId) {
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
        // 删除默认简历后允许当前用户暂时没有默认简历。
        resumeMapper.delete(id);
    }

    //添加求职意向
    @Override
    @Transactional
    public void addJobPreference(Long resumeId, JobPreference jobPreference, Long userId) {
        permissionService.requireJobSeeker(userId);
        findById(resumeId, userId);
        JobPreference exist = resumeMapper.findJobPreferenceByResumeId(resumeId);
        if (exist != null) {
            throw new BusinessException("求职意向已存在");
        }
        validateSalaryRange(jobPreference.getSalaryMin(), jobPreference.getSalaryMax());
        resumeMapper.addJobPreference(resumeId, jobPreference);
        refreshCompletionRate(resumeId, userId);
    }
    //获取求职意向
    @Override
    public JobPreference getJobPreference(Long resumeId, Long userId) {
        permissionService.requireJobSeeker(userId);
        findById(resumeId, userId);
        return resumeMapper.findJobPreferenceByResumeId(resumeId);
    }
    //修改求职意向
    @Override
    @Transactional
    public void updateJobPreference(Long preferenceId, JobPreference jobPreference, Long userId) {
        permissionService.requireJobSeeker(userId);
        JobPreference exist = resumeMapper.findJobPreferenceById(preferenceId);
        if (exist == null) {
            throw new BusinessException("求职意向不存在");
        }
        findById(exist.getResumeId(), userId);
        validateSalaryRange(jobPreference.getSalaryMin(), jobPreference.getSalaryMax());
        resumeMapper.updateJobPreference(preferenceId, jobPreference);
        refreshCompletionRate(exist.getResumeId(), userId);
    }
    //删除求职意向
    @Override
    @Transactional
    public void deleteJobPreference(Long preferenceId, Long userId) {
        permissionService.requireJobSeeker(userId);
        JobPreference exist = resumeMapper.findJobPreferenceById(preferenceId);
        if (exist == null) {
            throw new BusinessException("求职意向不存在");
        }
        findById(exist.getResumeId(), userId);
        resumeMapper.deleteJobPreference(preferenceId);
        refreshCompletionRate(exist.getResumeId(), userId);
    }

    //添加简历教育经历
    @Override
    @Transactional
    public void addResumeEducation(Long resumeId, ResumeEducation resumeEducation, Long userId) {
        permissionService.requireJobSeeker(userId);
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
        permissionService.requireJobSeeker(userId);
        findById(resumeId, userId);
        return resumeMapper.getResumeEducation(resumeId);
    }
    //修改简历教育经历
    @Override
    @Transactional
    public void updateResumeEducation(Long educationId, ResumeEducation resumeEducation, Long userId) {
        permissionService.requireJobSeeker(userId);
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
        permissionService.requireJobSeeker(userId);
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
    @Transactional
    public void addResumeExperience(Long resumeId, ResumeExperience experience, Long userId) {
        permissionService.requireJobSeeker(userId);
        findById(resumeId, userId);
        //设置默认排序顺序
        if (experience.getSortOrder() == null) {
            experience.setSortOrder(ResumeConstants.DEFAULT_SORT_ORDER);
        }
        validateDateRange(experience.getStartDate(), experience.getEndDate(), "工作经历");
        resumeMapper.addResumeExperience(resumeId, experience);
        refreshCompletionRate(resumeId, userId);
    }
    //获取简历工作经历
    @Override
    public List<ResumeExperience> getResumeExperience(Long resumeId, Long userId) {
        permissionService.requireJobSeeker(userId);
        findById(resumeId, userId);
        return resumeMapper.getResumeExperience(resumeId);
    }
    //修改简历工作经历
    @Override
    @Transactional
    public void updateResumeExperience(Long experienceId, ResumeExperience experience, Long userId) {
        permissionService.requireJobSeeker(userId);
        ResumeExperience exist = resumeMapper.findExperienceById(experienceId);
        if (exist == null) {
            throw new BusinessException("工作经历不存在");
        }
        findById(exist.getResumeId(), userId);
        if (experience.getSortOrder() == null) {
            experience.setSortOrder(exist.getSortOrder());
        }
        validateDateRange(experience.getStartDate(), experience.getEndDate(), "工作经历");
        resumeMapper.updateResumeExperience(experienceId, experience);
        refreshCompletionRate(exist.getResumeId(), userId);
    }
    //删除简历工作经历
    @Override
    @Transactional
    public void deleteResumeExperience(Long experienceId, Long userId) {
        permissionService.requireJobSeeker(userId);
        ResumeExperience exist = resumeMapper.findExperienceById(experienceId);
        if (exist == null) {
            throw new BusinessException("工作经历不存在");
        }
        findById(exist.getResumeId(), userId);
        resumeMapper.deleteResumeExperience(experienceId);
        refreshCompletionRate(exist.getResumeId(), userId);
    }

    //添加简历项目经历
    @Override
    @Transactional
    public void addResumeProject(Long resumeId, ResumeProject project, Long userId) {
        permissionService.requireJobSeeker(userId);
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
        permissionService.requireJobSeeker(userId);
        findById(resumeId, userId);
        return resumeMapper.getResumeProject(resumeId);
    }
    //修改简历项目经历
    @Override
    @Transactional
    public void updateResumeProject(Long projectId, ResumeProject project, Long userId) {
        permissionService.requireJobSeeker(userId);
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
        permissionService.requireJobSeeker(userId);
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
    @Transactional
    public void addResumeSkill(Long resumeId, ResumeSkill skill, Long userId) {
        permissionService.requireJobSeeker(userId);
        findById(resumeId, userId);
        if(skill.getSortOrder() == null ){
            skill.setSortOrder(ResumeConstants.DEFAULT_SORT_ORDER);
        }
        resumeMapper.addResumeSkill(resumeId, skill);
        refreshCompletionRate(resumeId, userId);
    }
    @Override
    public List<ResumeSkill> getResumeSkill(Long resumeId, Long userId) {
        permissionService.requireJobSeeker(userId);
        findById(resumeId, userId);
        return resumeMapper.getResumeSkill(resumeId);
    }
    @Override
    @Transactional
    public void updateResumeSkill(Long skillId, ResumeSkill skill, Long userId) {
        permissionService.requireJobSeeker(userId);
        ResumeSkill exist = resumeMapper.findSkillById(skillId);
        if (exist == null) {
            throw new BusinessException("技能不存在");
        }
        findById(exist.getResumeId(), userId);
        if (skill.getSortOrder() == null) {
            skill.setSortOrder(exist.getSortOrder());
        }
        resumeMapper.updateResumeSkill(skillId, skill);
        refreshCompletionRate(exist.getResumeId(), userId);

    }
    @Override
    @Transactional
    public void deleteResumeSkill(Long skillId, Long userId) {
        permissionService.requireJobSeeker(userId);
        ResumeSkill exist = resumeMapper.findSkillById(skillId);
        if (exist == null) {
            throw new BusinessException("技能不存在");
        }
        findById(exist.getResumeId(), userId);
        resumeMapper.deleteResumeSkill(skillId);
        refreshCompletionRate(exist.getResumeId(), userId);

    }

    //证书
    @Override
    public void addResumeCert(Long resumeId, ResumeCert cert, Long userId) {
        permissionService.requireJobSeeker(userId);
        findById(resumeId, userId);
        if(cert.getSortOrder() == null ){
            cert.setSortOrder(ResumeConstants.DEFAULT_SORT_ORDER);
        }
        resumeMapper.addResumeCert(resumeId, cert);
    }
    @Override
    public List<ResumeCert> getResumeCert(Long resumeId, Long userId) {
        permissionService.requireJobSeeker(userId);
        findById(resumeId, userId);
        return resumeMapper.getResumeCert(resumeId);
    }
    @Override
    public void updateResumeCert(Long certId, ResumeCert cert, Long userId) {
        permissionService.requireJobSeeker(userId);
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
        permissionService.requireJobSeeker(userId);
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
        permissionService.requireJobSeeker(userId);
        findById(resumeId, userId);
        if(link.getSortOrder() == null ){
            link.setSortOrder(ResumeConstants.DEFAULT_SORT_ORDER);
        }
        validateLinkUrl(link.getUrl());
        resumeMapper.addResumeLink(resumeId, link);
    }
    @Override
    public List<ResumeLink> getResumeLink(Long resumeId, Long userId) {
        permissionService.requireJobSeeker(userId);
        findById(resumeId, userId);
        return resumeMapper.getResumeLink(resumeId);
    }
    @Override
    public void updateResumeLink(Long linkId, ResumeLink link, Long userId) {
        permissionService.requireJobSeeker(userId);
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
        permissionService.requireJobSeeker(userId);
        ResumeLink exist = resumeMapper.findLinkById(linkId);
        if (exist == null) {
            throw new BusinessException("作品链接不存在");
        }
        findById(exist.getResumeId(), userId);
        resumeMapper.deleteResumeLink(linkId);

    }

    //简历全部详情
    @Override
    public ResumeDetailVO getResumeDetail(Long resumeId, Long userId) {
        permissionService.requireJobSeeker(userId);
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
        if (hasText(resume.getRealName())) completionRate += ResumeConstants.COMPLETION_ITEM_SCORE;
        if (hasText(resume.getEmail())) completionRate += ResumeConstants.COMPLETION_ITEM_SCORE;
        if (hasText(resume.getPhone())) completionRate += ResumeConstants.COMPLETION_ITEM_SCORE;
        if (hasText(resume.getCurrentCity())) completionRate += ResumeConstants.COMPLETION_ITEM_SCORE;
        if (jobPreference != null && hasText(jobPreference.getExpectedPosition())) completionRate += ResumeConstants.COMPLETION_ITEM_SCORE;
        if (jobPreference != null && hasText(jobPreference.getExpectedCity())) completionRate += ResumeConstants.COMPLETION_ITEM_SCORE;
        if (hasValidEducation(educations)) completionRate += ResumeConstants.COMPLETION_ITEM_SCORE;
        if (hasValidExperience(experiences) || hasValidProject(projects)) completionRate += ResumeConstants.COMPLETION_ITEM_SCORE;
        if (hasValidSkill(skills)) completionRate += ResumeConstants.COMPLETION_ITEM_SCORE;
        if (hasText(resume.getSummary())) completionRate += ResumeConstants.COMPLETION_ITEM_SCORE;

        return Math.min(completionRate, ResumeConstants.COMPLETE_RATE);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
                hasText(education.getSchoolName())
                        && hasText(education.getDegree())
                        && hasText(education.getMajor()));
    }

    private boolean hasValidExperience(List<ResumeExperience> experiences) {
        return experiences != null && experiences.stream().anyMatch(experience ->
                hasText(experience.getCompanyName())
                        && hasText(experience.getPosition())
                        && hasText(experience.getDescription()));
    }

    private boolean hasValidProject(List<ResumeProject> projects) {
        return projects != null && projects.stream().anyMatch(project ->
                hasText(project.getProjectName())
                        && hasText(project.getRole())
                        && hasText(project.getDescription()));
    }

    private boolean hasValidSkill(List<ResumeSkill> skills) {
        return skills != null && skills.stream().anyMatch(skill -> hasText(skill.getSkillName()));
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

