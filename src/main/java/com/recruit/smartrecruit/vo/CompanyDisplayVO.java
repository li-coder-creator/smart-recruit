package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.enums.CompanyIndustry;
import com.recruit.smartrecruit.entity.enums.CompanyEmployeeSize;
import com.recruit.smartrecruit.entity.enums.CompanyFinancingStage;
import com.recruit.smartrecruit.entity.enums.CompanyNature;
import com.recruit.smartrecruit.entity.enums.CompanyBusinessStatus;
import com.recruit.smartrecruit.entity.enums.CompanyRestPolicy;
import com.recruit.smartrecruit.entity.enums.CompanyOvertimePolicy;
import com.recruit.smartrecruit.entity.enums.CompanyBenefitCode;
import com.recruit.smartrecruit.entity.enums.CompanyRegistrationReviewStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 企业管理和求职者视图共用的公开字段白名单。
 * 不得加入账号归属、内部版本、审核意见或文件存储引用。
 */
@Data
@NoArgsConstructor
public class CompanyDisplayVO {
    private Long id;
    private String logo;
    /** 企业简称 / 品牌。 */
    private String name;
    /** 公司全称。 */
    private String fullName;
    /** 行业。 */
    private CompanyIndustry industry;
    /** 人员规模。 */
    private CompanyEmployeeSize employeeSize;
    /** 融资阶段。 */
    private CompanyFinancingStage financingStage;
    /** 企业性质。 */
    private CompanyNature companyNature;
    /** 企业官网。 */
    private String website;
    /** 城市 / 区域。 */
    private String city;
    /** 实际办公地址。 */
    private String address;
    /** 交通与到访说明。 */
    private String commuteDescription;
    /** 企业简介。 */
    private String description;
    /** 成立日期。 */
    private LocalDate foundedDate;
    /** 法定代表人。 */
    private String legalRepresentative;
    /** 注册资本及币种。 */
    private String registeredCapital;
    /** 经营状态。 */
    private CompanyBusinessStatus businessStatus;
    /** 统一社会信用代码。 */
    private String creditCode;
    /** 注册地址。 */
    private String registeredAddress;
    /** 经营范围。 */
    private String businessScope;
    /** 上班时间。 */
    private LocalTime workStartTime;
    /** 下班时间。 */
    private LocalTime workEndTime;
    /** 休息制度。 */
    private CompanyRestPolicy restPolicy;
    /** 加班情况。 */
    private CompanyOvertimePolicy overtimePolicy;
    /** 企业福利。 */
    private List<CompanyBenefitCode> benefits;
    /** 工作制度与福利说明。 */
    private String workNotes;
    /** 产品名称。 */
    private String productName;
    /** 产品介绍。 */
    private String productDescription;
    /** 团队或管理者名称。 */
    private String teamName;
    /** 介绍类型或职务。 */
    private String teamRole;
    /** 团队或管理者简介。 */
    private String teamBio;

    /** 由后续 Service 根据新版必填项计算，不能因企业准入已通过就设为 true。 */
    private Boolean basicProfileComplete;
    private CompanyRegistrationReviewStatus registrationReviewStatus;
    /** 只有当前工商版本审核通过，才为 true。 */
    private Boolean registrationVerified;
    /** 依据未移除且审核通过的营业执照材料计算。 */
    private Boolean businessLicenseVerified;
    private Long photoCount;
}
