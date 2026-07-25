package com.recruit.smartrecruit.service;

import com.recruit.smartrecruit.entity.Company;

public interface CompanyService {

    void add(Company company);

    Company findByUserId(Long userId);

    void update(Company newCompany);

    void delete(Long userId);
}
