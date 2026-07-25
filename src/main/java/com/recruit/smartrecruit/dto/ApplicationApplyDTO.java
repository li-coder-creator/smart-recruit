package com.recruit.smartrecruit.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationApplyDTO {
    @NotBlank(message = "岗位ID不能为空")
    private Long jobId;
    @NotBlank(message = "简历ID不能为空")
    private Long resumeId;
}
