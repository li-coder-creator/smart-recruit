package com.recruit.smartrecruit.service;

import com.recruit.smartrecruit.dto.ApplicationApplyDTO;
import com.recruit.smartrecruit.dto.ApplicationStatusUpdateDTO;
import com.recruit.smartrecruit.entity.Application;

import java.util.List;

public interface ApplicationService {
    void apply(ApplicationApplyDTO dto, Long userId);

    List<Application> findCompanyApplication(Long userId);

    Application findCompanyApplicationById(Long id, Long userId);

    void updateStatus(Long id, Long userId, ApplicationStatusUpdateDTO dto);
}
