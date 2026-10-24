package com.recruit.smartrecruit.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CompanyRegistrationReviewDetailVO extends CompanyRegistrationReviewVO {
    private Long reviewerUserId;
    private String reviewComment;
    private CompanyRegistrationSnapshotVO snapshot;
}
