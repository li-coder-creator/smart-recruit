package com.recruit.smartrecruit.service;

import com.recruit.smartrecruit.common.PageResult;
import com.recruit.smartrecruit.dto.CompanyRegistrationReviewDecisionDTO;
import com.recruit.smartrecruit.entity.enums.CompanyReviewStatus;
import com.recruit.smartrecruit.vo.*;

public interface CompanyRegistrationReviewService {
    PageResult<CompanyRegistrationReviewVO> listReviews(Long userId, CompanyReviewStatus status, int page, int pageSize);
    CompanyRegistrationReviewDetailVO getDetail(Long userId, Long reviewId);
    void decide(Long userId, Long reviewId, CompanyRegistrationReviewDecisionDTO dto);
    PageResult<CompanyRegistrationReviewHistoryVO> listMyHistory(Long userId, CompanyReviewStatus status,
                                                               int page, int pageSize);
}
