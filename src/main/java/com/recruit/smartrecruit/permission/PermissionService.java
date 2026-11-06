package com.recruit.smartrecruit.permission;

import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.User;

public interface PermissionService {
    User requireJobSeeker(Long userId);
    Company requireCompany(Long userId);
    Company requireApprovedCompany(Long userId);
    /** 公开岗位入口按所属企业校验；不用于历史投递读取。 */
    void requireApprovedJobCompany(Long companyId);
    User requireAdmin(Long userId);
}
