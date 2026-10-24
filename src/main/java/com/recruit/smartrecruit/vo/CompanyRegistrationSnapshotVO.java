package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.enums.CompanyBusinessStatus;
import java.time.LocalDate;
import lombok.Data;

/** 一次提交的八个工商字段，不以企业当前资料替代历史证据。 */
@Data
public class CompanyRegistrationSnapshotVO {
    private String fullName;
    private LocalDate foundedDate;
    private String legalRepresentative;
    private String registeredCapital;
    private CompanyBusinessStatus businessStatus;
    private String creditCode;
    private String registeredAddress;
    private String businessScope;
}
