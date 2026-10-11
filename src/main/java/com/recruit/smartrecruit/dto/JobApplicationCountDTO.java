package com.recruit.smartrecruit.dto;

import lombok.Data;

/** Mapper 到 Service 的聚合查询结果，仅内部使用，不作为 HTTP 入参或响应。 */
@Data
public class JobApplicationCountDTO {
    private Long jobId;
    private Long applicationCount;
    private Long pendingApplicationCount;
    private Long currentInterviewApplicationCount;
}
