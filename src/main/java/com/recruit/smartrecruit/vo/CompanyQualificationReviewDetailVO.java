package com.recruit.smartrecruit.vo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import com.recruit.smartrecruit.entity.enums.*;
@Data
@EqualsAndHashCode(callSuper = true)
public class CompanyQualificationReviewDetailVO extends CompanyQualificationReviewVO {
    private Long reviewerUserId;
    private String reviewComment;
    private CompanyQualificationSnapshotVO snapshot;
    private String downloadUrl;
}
