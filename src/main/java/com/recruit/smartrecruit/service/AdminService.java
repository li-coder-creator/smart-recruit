package com.recruit.smartrecruit.service;

import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.enums.CompanyStatus;

import java.util.List;

public interface AdminService {
    List<Company> findPendingCompanies();


    void updateCompanyStatus(Long id, CompanyStatus companyStatus);

}
