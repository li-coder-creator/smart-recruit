package com.recruit.smartrecruit.entity;

import com.recruit.smartrecruit.constant.ResumeValidationConstants;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;



import java.time.LocalDate;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ResumeCert {
    private Long id;

    private Long resumeId;

    @NotBlank(message = "证书名称不能为空")
    @Size(max = ResumeValidationConstants.MEDIUM_TEXT_MAX_LENGTH, message = "证书名称长度不能超过100个字")
    private String certName;

    @Size(max = ResumeValidationConstants.MEDIUM_TEXT_MAX_LENGTH, message = "发证机构长度不能超过100个字")
    private String issuer;

    private LocalDate awardDate;

    @Size(max = ResumeValidationConstants.DESCRIPTION_MAX_LENGTH, message = "证书描述长度不能超过1000个字")
    private String description;

    @PositiveOrZero(message = "排序值不能小于0")
    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
