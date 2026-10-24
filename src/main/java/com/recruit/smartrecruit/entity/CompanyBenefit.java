package com.recruit.smartrecruit.entity;

import com.recruit.smartrecruit.entity.enums.CompanyBenefitCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyBenefit {
    private Long id;
    private Long companyId;
    private CompanyBenefitCode benefitCode;
    private Integer sortOrder;
}
