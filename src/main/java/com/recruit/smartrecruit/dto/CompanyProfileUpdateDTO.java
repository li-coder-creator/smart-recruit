package com.recruit.smartrecruit.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.recruit.smartrecruit.constant.CompanyValidationConstants;
import com.recruit.smartrecruit.entity.enums.CompanyIndustry;
import com.recruit.smartrecruit.entity.enums.CompanyEmployeeSize;
import com.recruit.smartrecruit.entity.enums.CompanyFinancingStage;
import com.recruit.smartrecruit.entity.enums.CompanyNature;
import com.recruit.smartrecruit.entity.enums.CompanyBusinessStatus;
import com.recruit.smartrecruit.entity.enums.CompanyRestPolicy;
import com.recruit.smartrecruit.entity.enums.CompanyOvertimePolicy;
import com.recruit.smartrecruit.entity.enums.CompanyBenefitCode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;

/**
 * 完整表单保存请求，不接受企业归属及审核结果。
 * profileVersion 为客户端预期版本，不能作为更新后的版本直接写入。
 */
@Data
@NoArgsConstructor
public class CompanyProfileUpdateDTO {
    /** 企业简称 / 品牌。 */
    @NotBlank(message = "企业简称 / 品牌不能为空")
    @Size(max = CompanyValidationConstants.NAME_MAX_LENGTH, message = "企业简称 / 品牌不能超过{max}个字符")
    private String name;

    /** 公司全称。 */
    @NotBlank(message = "公司全称不能为空")
    @Size(max = CompanyValidationConstants.FULL_NAME_MAX_LENGTH, message = "公司全称不能超过{max}个字符")
    private String fullName;

    /** 行业。 */
    @NotNull(message = "行业不能为空")
    private CompanyIndustry industry;

    /** 人员规模。 */
    @NotNull(message = "人员规模不能为空")
    private CompanyEmployeeSize employeeSize;

    /** 融资阶段。 */
    private CompanyFinancingStage financingStage;

    /** 企业性质。 */
    private CompanyNature companyNature;

    /** 企业官网。 */
    @Size(max = CompanyValidationConstants.WEBSITE_MAX_LENGTH, message = "企业官网不能超过{max}个字符")
    @URL(message = "企业官网地址格式不正确")
    @Pattern(regexp = CompanyValidationConstants.HTTP_WEBSITE_PATTERN, message = "企业官网必须以 http:// 或 https:// 开头")
    private String website;

    /** 城市 / 区域。 */
    @NotBlank(message = "城市 / 区域不能为空")
    @Size(max = CompanyValidationConstants.CITY_MAX_LENGTH, message = "城市 / 区域不能超过{max}个字符")
    private String city;

    /** 实际办公地址。 */
    @NotBlank(message = "实际办公地址不能为空")
    @Size(max = CompanyValidationConstants.ADDRESS_MAX_LENGTH, message = "实际办公地址不能超过{max}个字符")
    private String address;

    /** 交通与到访说明。 */
    @Size(max = CompanyValidationConstants.COMMUTE_DESCRIPTION_MAX_LENGTH, message = "交通与到访说明不能超过{max}个字符")
    private String commuteDescription;

    /** 企业简介。 */
    @NotBlank(message = "企业简介不能为空")
    @Size(max = CompanyValidationConstants.DESCRIPTION_MAX_LENGTH, message = "企业简介不能超过{max}个字符")
    private String description;

    /** 成立日期。 */
    @PastOrPresent(message = "成立日期不能晚于今天")
    private LocalDate foundedDate;

    /** 法定代表人。 */
    @Size(max = CompanyValidationConstants.LEGAL_REPRESENTATIVE_MAX_LENGTH, message = "法定代表人不能超过{max}个字符")
    private String legalRepresentative;

    /** 注册资本及币种。 */
    @Size(max = CompanyValidationConstants.REGISTERED_CAPITAL_MAX_LENGTH, message = "注册资本及币种不能超过{max}个字符")
    private String registeredCapital;

    /** 经营状态。 */
    private CompanyBusinessStatus businessStatus;

    /** 统一社会信用代码。 */
    @Size(max = CompanyValidationConstants.CREDIT_CODE_MAX_LENGTH, message = "统一社会信用代码不能超过{max}个字符")
    @Pattern(regexp = CompanyValidationConstants.CREDIT_CODE_PATTERN, message = "统一社会信用代码必须为18位大写字母或数字")
    private String creditCode;

    /** 注册地址。 */
    @Size(max = CompanyValidationConstants.REGISTERED_ADDRESS_MAX_LENGTH, message = "注册地址不能超过{max}个字符")
    private String registeredAddress;

    /** 经营范围。 */
    @Size(max = CompanyValidationConstants.BUSINESS_SCOPE_MAX_LENGTH, message = "经营范围不能超过{max}个字符")
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
    @NotNull(message = "请提交福利列表，清空时传空数组")
    @Size(max = CompanyValidationConstants.BENEFITS_MAX_COUNT, message = "福利最多选择{max}项")
    private List<@NotNull(message = "福利项不能为空") CompanyBenefitCode> benefits;

    /** 工作制度与福利说明。 */
    @Size(max = CompanyValidationConstants.WORK_NOTES_MAX_LENGTH, message = "工作制度与福利说明不能超过{max}个字符")
    private String workNotes;

    /** 产品名称。 */
    @Size(max = CompanyValidationConstants.PRODUCT_NAME_MAX_LENGTH, message = "产品名称不能超过{max}个字符")
    private String productName;

    /** 产品介绍。 */
    @Size(max = CompanyValidationConstants.PRODUCT_DESCRIPTION_MAX_LENGTH, message = "产品介绍不能超过{max}个字符")
    private String productDescription;

    /** 团队或管理者名称。 */
    @Size(max = CompanyValidationConstants.TEAM_NAME_MAX_LENGTH, message = "团队或管理者名称不能超过{max}个字符")
    private String teamName;

    /** 介绍类型或职务。 */
    @Size(max = CompanyValidationConstants.TEAM_ROLE_MAX_LENGTH, message = "介绍类型或职务不能超过{max}个字符")
    private String teamRole;

    /** 团队或管理者简介。 */
    @Size(max = CompanyValidationConstants.TEAM_BIO_MAX_LENGTH, message = "团队或管理者简介不能超过{max}个字符")
    private String teamBio;

    @NotNull(message = "资料版本不能为空")
    @Min(value = CompanyValidationConstants.MIN_PROFILE_VERSION, message = "资料版本不能为负数")
    private Long profileVersion;

    @JsonIgnore
    @AssertTrue(message = "上班时间和下班时间必须同时填写或同时留空")
    public boolean isWorkTimePairValid() {
        return (workStartTime == null) == (workEndTime == null);
    }

    @JsonIgnore
    @AssertTrue(message = "福利不能重复选择")
    public boolean isBenefitsUnique() {
        return benefits == null || new HashSet<>(benefits).size() == benefits.size();
    }

    @JsonIgnore
    @AssertTrue(message = "填写产品介绍时必须提供产品名称")
    public boolean isProductPairValid() {
        return productDescription == null || productName != null;
    }

    @JsonIgnore
    @AssertTrue(message = "填写团队职务或简介时必须提供团队或管理者名称")
    public boolean isTeamPairValid() {
        return (teamRole == null && teamBio == null) || teamName != null;
    }

    // Jackson 调用 setter 时先归一化，随后再进行 Bean Validation。
    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    public void setName(String name) {
        this.name = normalizeText(name);
    }

    public void setFullName(String fullName) {
        this.fullName = normalizeText(fullName);
    }

    public void setWebsite(String website) {
        this.website = normalizeText(website);
    }

    public void setCity(String city) {
        this.city = normalizeText(city);
    }

    public void setAddress(String address) {
        this.address = normalizeText(address);
    }

    public void setCommuteDescription(String commuteDescription) {
        this.commuteDescription = normalizeText(commuteDescription);
    }

    public void setDescription(String description) {
        this.description = normalizeText(description);
    }

    public void setLegalRepresentative(String legalRepresentative) {
        this.legalRepresentative = normalizeText(legalRepresentative);
    }

    public void setRegisteredCapital(String registeredCapital) {
        this.registeredCapital = normalizeText(registeredCapital);
    }

    public void setCreditCode(String creditCode) {
        this.creditCode = normalizeText(creditCode);
    }

    public void setRegisteredAddress(String registeredAddress) {
        this.registeredAddress = normalizeText(registeredAddress);
    }

    public void setBusinessScope(String businessScope) {
        this.businessScope = normalizeText(businessScope);
    }

    public void setWorkNotes(String workNotes) {
        this.workNotes = normalizeText(workNotes);
    }

    public void setProductName(String productName) {
        this.productName = normalizeText(productName);
    }

    public void setProductDescription(String productDescription) {
        this.productDescription = normalizeText(productDescription);
    }

    public void setTeamName(String teamName) {
        this.teamName = normalizeText(teamName);
    }

    public void setTeamRole(String teamRole) {
        this.teamRole = normalizeText(teamRole);
    }

    public void setTeamBio(String teamBio) {
        this.teamBio = normalizeText(teamBio);
    }

}
