package com.recruit.smartrecruit.entity;

import com.recruit.smartrecruit.entity.enums.CompanyReviewType;
import com.recruit.smartrecruit.entity.enums.CompanyReviewStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyReviewRecord {
    private Long id;
    private Long companyId;
    private CompanyReviewType reviewType;
    /** 材料审核必填，工商审核为空。 */
    private Long qualificationId;
    /** 工商审核必填，材料审核为空。 */
    private Long registrationVersion;
    /** 提交时的资料快照，由服务端生成并序列化。 */
    private String snapshotJson;
    private CompanyReviewStatus reviewStatus;
    private Long reviewerUserId;
    private String reviewComment;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
}
