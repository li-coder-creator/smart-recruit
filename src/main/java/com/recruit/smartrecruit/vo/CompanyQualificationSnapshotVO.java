package com.recruit.smartrecruit.vo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import com.recruit.smartrecruit.entity.enums.*;
@Data

public class CompanyQualificationSnapshotVO {
    private Long qualificationId;
    private CompanyQualificationType qualificationType;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private String sha256;
}
