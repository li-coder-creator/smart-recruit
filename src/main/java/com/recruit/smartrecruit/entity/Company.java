package com.recruit.smartrecruit.entity;

import com.recruit.smartrecruit.entity.enums.CompanyStatus;
import com.recruit.smartrecruit.entity.enums.CompanyIndustry;
import com.recruit.smartrecruit.entity.enums.CompanyEmployeeSize;
import com.recruit.smartrecruit.entity.enums.CompanyFinancingStage;
import com.recruit.smartrecruit.entity.enums.CompanyNature;
import com.recruit.smartrecruit.entity.enums.CompanyBusinessStatus;
import com.recruit.smartrecruit.entity.enums.CompanyRestPolicy;
import com.recruit.smartrecruit.entity.enums.CompanyOvertimePolicy;
import com.recruit.smartrecruit.entity.enums.CompanyRegistrationReviewStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Company {
    private Long id;
    private Long userId;
    @NotBlank(message = "企业名称不能为空")
    private String name;
    private String description;
    private String logo;
    private String city;
    private String address;
    private CompanyStatus status;

    // 企业展示资料。
    private String fullName;
    private CompanyIndustry industry;
    private CompanyEmployeeSize employeeSize;
    private CompanyFinancingStage financingStage;
    private CompanyNature companyNature;
    private String website;
    private String commuteDescription;

    // 工商资料，与企业准入 status 分开核验。
    private LocalDate foundedDate;
    private String legalRepresentative;
    private String registeredCapital;
    private CompanyBusinessStatus businessStatus;
    private String creditCode;
    private String registeredAddress;
    private String businessScope;

    // 工作制度与福利补充，福利多选项由子表存储。
    private LocalTime workStartTime;
    private LocalTime workEndTime;
    private CompanyRestPolicy restPolicy;
    private CompanyOvertimePolicy overtimePolicy;
    private String workNotes;

    // 当前原型各支持一组产品与团队资料。
    private String productName;
    private String productDescription;
    private String teamName;
    private String teamRole;
    private String teamBio;

    // 版本由 Service 控制，不能作为普通可编辑字段复制。
    private Long profileVersion;
    private Long registrationVersion;
    private CompanyRegistrationReviewStatus registrationReviewStatus;
    private Long verifiedRegistrationVersion;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
