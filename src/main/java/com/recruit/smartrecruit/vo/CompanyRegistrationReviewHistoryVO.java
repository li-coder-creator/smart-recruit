package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.enums.CompanyReviewStatus;
import java.time.LocalDateTime;
import lombok.Data;

/** 企业自己的审核反馈，不返回管理员账号或数据库原始快照字符串。 */
@Data
public class CompanyRegistrationReviewHistoryVO {
    private Long id;
    private Long registrationVersion;
    private Long currentRegistrationVersion;
    private Boolean currentVersion;
    private CompanyReviewStatus reviewStatus;
    private String reviewComment;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private CompanyRegistrationSnapshotVO snapshot;
}
