package com.recruit.smartrecruit.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Application {
    private Long id;
    @NotNull(message = "用户ID不能为空")
    private Long userId;
    @NotNull(message = "岗位ID不能为空")
    private Long jobId;
    @NotNull(message = "简历ID不能为空")
    private Long resumeId;
    @NotNull(message="投递状态不能为空")
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
