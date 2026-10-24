package com.recruit.smartrecruit.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.recruit.smartrecruit.constant.CompanyReviewConstants;
import com.recruit.smartrecruit.entity.enums.CompanyReviewDecision;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 审核人、审核时间和最终状态均由服务端确定。 */
@Data
public class CompanyRegistrationReviewDecisionDTO {
    @NotNull(message = "请选择审核结果")
    private CompanyReviewDecision decision;

    @NotNull(message = "工商资料版本不能为空")
    @Min(value = CompanyReviewConstants.MIN_REGISTRATION_VERSION, message = "工商资料版本不能为负数")
    private Long registrationVersion;

    @Size(max = CompanyReviewConstants.REVIEW_COMMENT_MAX_LENGTH, message = "审核意见不能超过{max}个字符")
    private String reviewComment;

    public void setReviewComment(String value) {
        reviewComment = value == null || value.strip().isEmpty() ? null : value.strip();
    }

    @JsonIgnore
    @AssertTrue(message = "驳回时必须填写审核意见")
    public boolean isRejectionCommentValid() {
        return decision != CompanyReviewDecision.REJECT || reviewComment != null;
    }
}
