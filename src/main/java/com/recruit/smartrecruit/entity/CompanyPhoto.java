package com.recruit.smartrecruit.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyPhoto {
    private Long id;
    private Long companyId;
    /** 内部存储引用，不直接作为公开 URL 返回。 */
    private String storageKey;
    private String originalFilename;
    private String contentType;
    /** 文件大小，单位字节。 */
    private Long fileSize;
    private Integer sortOrder;
    private LocalDateTime createTime;
}
