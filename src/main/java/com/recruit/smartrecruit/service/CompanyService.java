package com.recruit.smartrecruit.service;

import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.entity.Company;

import java.util.Map;

public interface CompanyService {

    void add(Company company);

    Company findByUserId(Long userId);

    void update(Company newCompany);

    void delete(Long userId);


}
