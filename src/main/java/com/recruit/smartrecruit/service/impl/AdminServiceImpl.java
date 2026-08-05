package com.recruit.smartrecruit.service.impl;

import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.enums.CompanyStatus;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.CompanyMapper;
import com.recruit.smartrecruit.service.AdminService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminServiceImpl implements AdminService {
    public final CompanyMapper companyMapper;
    public AdminServiceImpl(CompanyMapper companyMapper) {
        this.companyMapper = companyMapper;
    }

    @Override
    public List<Company> findPendingCompanies(){
        return companyMapper.findByStatus(CompanyStatus.PENDING);
    }
    //审核通过或拒绝
    @Override
    public void updateCompanyStatus(Long id, CompanyStatus companyStatus) {
        companyMapper.updateStatus(id,companyStatus);
    }

}
