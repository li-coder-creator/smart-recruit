package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.JobPreference;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;
import com.recruit.smartrecruit.entity.enums.JobDirection;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 独立响应契约；仅从已鉴权读取的实体复制，不执行查询或推断未知信息。 */
@Data
@NoArgsConstructor
public class JobPreferenceVO {
    private Long id;
    private Long resumeId;
    private String expectedPosition;
    private String expectedCity;
    private Integer salaryMin;
    private Integer salaryMax;
    private String salaryText;
    private LocalDate availableDate;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private JobDirection directionCode;

    public JobPreferenceVO(JobPreference source) {
        BeanUtils.copyProperties(source, this);
    }
}
