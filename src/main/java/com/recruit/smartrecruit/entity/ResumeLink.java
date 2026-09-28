package com.recruit.smartrecruit.entity;

import com.recruit.smartrecruit.constant.ResumeValidationConstants;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ResumeLink {
    private Long id;

    private Long resumeId;

    @NotBlank(message = "链接名称不能为空")
    @Size(max = ResumeValidationConstants.MEDIUM_TEXT_MAX_LENGTH, message = "链接名称长度不能超过100个字")
    private String linkName;

    @Size(max = ResumeValidationConstants.SHORT_TEXT_MAX_LENGTH, message = "链接类型长度不能超过50个字")
    private String linkType;

    @NotBlank(message = "链接地址不能为空")
    @Size(max = ResumeValidationConstants.URL_MAX_LENGTH, message = "链接地址长度不能超过2048个字符")
    private String url;

    @Size(max = ResumeValidationConstants.DESCRIPTION_MAX_LENGTH, message = "链接描述长度不能超过500个字")
    private String description;

    @PositiveOrZero(message = "排序值不能小于0")
    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
