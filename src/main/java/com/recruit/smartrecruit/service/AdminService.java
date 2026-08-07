package com.recruit.smartrecruit.service;

import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.enums.CompanyStatus;

import java.util.List;

public interface AdminService {
    List<Company> findPendingCompanies(Long userdId);


    void updateCompanyStatus(Long userdId,Long id, CompanyStatus companyStatus);

}
