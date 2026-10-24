package com.recruit.smartrecruit.service;
import com.recruit.smartrecruit.common.PageResult;
import com.recruit.smartrecruit.dto.*;
import com.recruit.smartrecruit.entity.enums.*;
import com.recruit.smartrecruit.vo.*;
public interface CompanyQualificationReviewService {
    PageResult<CompanyQualificationReviewVO> list(Long userId,CompanyReviewStatus status,CompanyQualificationType type,int page,int pageSize);
    CompanyQualificationReviewDetailVO detail(Long userId,Long id);
    CompanyFileContent readFile(Long userId,Long id);
    void decide(Long userId,Long id,CompanyQualificationReviewDecisionDTO dto);
    PageResult<CompanyQualificationReviewHistoryVO> history(Long userId,CompanyReviewStatus status,int page,int pageSize);
}
