package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.enums.CompanyQualificationType;
import com.recruit.smartrecruit.entity.enums.CompanyReviewStatus;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CompanyQualificationVO {
    private Long id;
    private CompanyQualificationType qualificationType;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private CompanyReviewStatus reviewStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String downloadUrl;
}
