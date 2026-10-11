package com.recruit.smartrecruit.entity;

import com.recruit.smartrecruit.entity.enums.JobOperation;
import lombok.Data;

import java.time.LocalDateTime;

/** 岗位成功操作的业务记录，和岗位修改同事务写入。 */
@Data
public class JobActivity {
    private Long id;
    private Long jobId;
    private Long operatorUserId;
    private String operatorName;
    private JobOperation operation;
    private LocalDateTime createTime;
}
