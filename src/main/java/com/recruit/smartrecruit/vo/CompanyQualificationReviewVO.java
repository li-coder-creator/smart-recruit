package com.recruit.smartrecruit.vo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import com.recruit.smartrecruit.entity.enums.*;
@Data

public class CompanyQualificationReviewVO {
    private Long id;
    private Long companyId;
    private String companyName;
    private Long qualificationId;
    private CompanyQualificationType qualificationType;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private Boolean removed;
    private CompanyReviewStatus reviewStatus;
    private Boolean reviewable;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
}
