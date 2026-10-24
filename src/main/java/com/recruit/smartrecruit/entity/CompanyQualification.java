package com.recruit.smartrecruit.entity;

import com.recruit.smartrecruit.entity.enums.CompanyQualificationType;
import com.recruit.smartrecruit.entity.enums.CompanyReviewStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyQualification {
    private Long id;
    private Long companyId;
    private CompanyQualificationType qualificationType;
    /** 私有文件引用，访问需验证身份和企业归属。 */
    private String storageKey;
    private String originalFilename;
    private String contentType;
    /** 文件大小，单位字节。 */
    private Long fileSize;
    private CompanyReviewStatus reviewStatus;
    /** 移除材料采用逻辑删除，保留审核关联。 */
    private Boolean deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
