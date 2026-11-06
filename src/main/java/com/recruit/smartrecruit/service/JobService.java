package com.recruit.smartrecruit.service;

import com.recruit.smartrecruit.common.PageResult;
import com.recruit.smartrecruit.dto.JobContentSaveDTO;
import com.recruit.smartrecruit.entity.Job;
import com.recruit.smartrecruit.entity.enums.JobListFilter;
import com.recruit.smartrecruit.entity.enums.JobBrowseSort;
import com.recruit.smartrecruit.vo.*;
import jakarta.validation.Valid;

import java.util.List;

public interface JobService {
    void add(JobContentSaveDTO jobSaveDTO, Long userId);

    void saveDraft(JobContentSaveDTO jobSaveDTO, Long userId);

    void update(JobContentSaveDTO jobSaveDTO,Long id, Long userId);

    void publishDraft(long id, Long userId);

    void pauseJob(long id, Long userId);

    void resumeJob(long id, Long userId);

    void endJob(long id, Long userId);

    void delete(long id, Long userId);

    PageResult<JobListVO> findJob(Long userId, int pageNum, int pageSize, String keyword, JobBrowseSort sort);

    PageResult<CompanyJobListVO> findCompanyJob(Long userId, int pageNum, int pageSize ,String keyword, JobListFilter filter);

    JobContentDetailVO findById(long id, Long userId);

    JobContentDetailVO findByCompanyId(long id, Long userId);

    CompanyJobOverviewVO companyOverview(Long userId);

    JobRecruitmentProgressVO findJobRecruitmentProgressById(long id, Long userId);

    List<RecentApplicationVO> findJobRecentById(long id, Long userId, Integer limit);

    List<JobActivityVO> findJobActivityById(long id, Long userId, Integer limit);
}
