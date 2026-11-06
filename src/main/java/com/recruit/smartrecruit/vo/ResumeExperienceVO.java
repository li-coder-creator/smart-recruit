package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.ResumeExperience;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;
import com.recruit.smartrecruit.entity.enums.ExperienceType;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 独立响应契约；仅从已鉴权读取的实体复制，不执行查询或推断未知信息。 */
@Data
@NoArgsConstructor
public class ResumeExperienceVO {
    private Long id;
    private Long resumeId;
    private String companyName;
    private String position;
    private LocalDate startDate;
    private LocalDate endDate;
    private String periodText;
    private String description;
    private String achievement;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private ExperienceType experienceType;
    private Boolean isCurrent;

    public ResumeExperienceVO(ResumeExperience source) {
        BeanUtils.copyProperties(source, this);
    }
}
