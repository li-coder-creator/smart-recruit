package com.recruit.smartrecruit.permission;

import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.User;

public interface PermissionService {
    User requireJobSeeker(Long userId);
    Company requireCompany(Long userId);
    Company requireApprovedCompany(Long userId);
    User requireAdmin(Long userId);
}
