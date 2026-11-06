package com.recruit.smartrecruit.service.impl;

import com.recruit.smartrecruit.constant.RecommendationQueryConstants;
import com.recruit.smartrecruit.dto.RecommendationJobRow;
import com.recruit.smartrecruit.entity.*;
import com.recruit.smartrecruit.entity.enums.*;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.JobRecommendationMapper;
import com.recruit.smartrecruit.permission.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** 只读快照边界；返回后事务已结束，应用服务再构建特征和调用计算器。 */
@Service
public class RecommendationSnapshotReader {
    private final JobRecommendationMapper mapper;
    private final PermissionService permission;
    public RecommendationSnapshotReader(JobRecommendationMapper mapper,PermissionService permission) {this.mapper=mapper;this.permission=permission;}
    public record ResumeInput(Resumebasic resume,JobPreference preference,List<ResumeEducation> education,
                              List<ResumeExperience> experience,List<ResumeSkill> skills) {
        public ResumeInput {education=List.copyOf(education);experience=List.copyOf(experience);skills=List.copyOf(skills);}
    }
    public record Snapshot(ResumeInput resume,List<RecommendationJobRow> jobs,Map<Long,List<JobRequiredAbility>> abilities) {
        public Snapshot {jobs=List.copyOf(jobs);abilities=Map.copyOf(abilities);}
    }
    private ResumeInput ownedResume(Long userId,Long resumeId) {
        permission.requireJobSeeker(userId);
        if(resumeId==null||resumeId<=0) throw new BusinessException("请选择有效简历");
        Resumebasic resume=mapper.findOwnedResume(resumeId,userId);
        if(resume==null) throw new BusinessException("简历不存在或无访问权限");
        return new ResumeInput(resume,mapper.preference(resumeId),mapper.education(resumeId),mapper.experience(resumeId),mapper.skills(resumeId));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ResumeInput context(Long userId,Long resumeId) {return ownedResume(userId,resumeId);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Snapshot load(Long userId,Long resumeId,LocalDate asOfDate,boolean excludeApplied,boolean excludeExpired) {
        ResumeInput resume=ownedResume(userId,resumeId);
        List<RecommendationJobRow> jobs=mapper.jobs(userId,JobStatus.ACTIVE,CompanyStatus.APPROVED,asOfDate,excludeApplied,excludeExpired);
        List<Long> ids=jobs.stream().filter(j->j.getRequiredAbilitiesSource()==RequiredAbilitiesSource.STRUCTURED).map(Job::getId).toList();
        Map<Long,List<JobRequiredAbility>> abilities=new HashMap<>();
        for(int start=0;start<ids.size();start+=RecommendationQueryConstants.ABILITY_READ_BATCH_SIZE) {
            List<Long> batch=ids.subList(start,Math.min(ids.size(),start+RecommendationQueryConstants.ABILITY_READ_BATCH_SIZE));
            mapper.abilities(batch).stream().collect(Collectors.groupingBy(JobRequiredAbility::getJobId)).forEach((id,items)->abilities.put(id,List.copyOf(items)));
        }
        // 显式保留空数组；未出现的STRUCTURED岗位不允许回退旧字符串。
        ids.forEach(id->abilities.putIfAbsent(id,List.of()));
        return new Snapshot(resume,jobs,abilities);
    }
}
