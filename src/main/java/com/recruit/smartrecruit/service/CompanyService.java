package com.recruit.smartrecruit.service;

import com.recruit.smartrecruit.dto.CompanyProfileUpdateDTO;
import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.vo.CompanyProfileVO;
import com.recruit.smartrecruit.vo.CompanyPublicProfileVO;
import com.recruit.smartrecruit.vo.JobListVO;
import com.recruit.smartrecruit.common.PageResult;
import jakarta.validation.Valid;

public interface CompanyService {

    Company findByUserId(Long userId);

    CompanyProfileVO getMyProfile(Long userId);

    void updateProfile(Long userId, @Valid CompanyProfileUpdateDTO dto);

    CompanyPublicProfileVO previewProfile(Long userId);

    CompanyPublicProfileVO getPublicProfile(Long userId, Long companyId);

    PageResult<JobListVO> listActiveJobs(Long userId, Long companyId, int page, int pageSize);
}
