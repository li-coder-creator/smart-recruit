package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.enums.CompanyStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 企业本人管理视图，不能作为求职者接口返回类型。 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CompanyProfileVO extends CompanyDisplayVO {
    private CompanyStatus status;
    private Long profileVersion;
    private Long registrationVersion;
    private Long verifiedRegistrationVersion;
    /** DTO 字段名列表，供企业定位待完善项。 */
    private List<String> missingRequiredFields;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
