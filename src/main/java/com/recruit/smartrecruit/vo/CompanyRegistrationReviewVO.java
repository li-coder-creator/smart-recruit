package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.enums.CompanyReviewStatus;
import java.time.LocalDateTime;
import lombok.Data;

/** 管理员审核列表，不加载完整快照。 */
@Data
public class CompanyRegistrationReviewVO {
    private Long id;
    private Long companyId;
    private String companyName;
    private Long registrationVersion;
    private Long currentRegistrationVersion;
    private CompanyReviewStatus reviewStatus;
    private Boolean currentVersion;
    private Boolean reviewable;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
}
