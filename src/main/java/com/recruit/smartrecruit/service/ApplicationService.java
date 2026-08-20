package com.recruit.smartrecruit.service;

import com.recruit.smartrecruit.dto.ApplicationApplyDTO;
import com.recruit.smartrecruit.dto.ApplicationStatusUpdateDTO;
import com.recruit.smartrecruit.entity.Application;
import com.recruit.smartrecruit.vo.ApplicationVO;

import java.util.List;

public interface ApplicationService {
    void apply(ApplicationApplyDTO dto, Long userId);

    List<ApplicationVO> findCompanyApplication(Long userId);

    ApplicationVO findApplicationDetail(Long id, Long userId);

    void updateStatus(Long id, Long userId, ApplicationStatusUpdateDTO dto);

    List<ApplicationVO> findJobseekerApplication(Long userId);
}
