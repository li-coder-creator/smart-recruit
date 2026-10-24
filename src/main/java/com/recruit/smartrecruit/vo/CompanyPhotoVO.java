package com.recruit.smartrecruit.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CompanyPhotoVO {
    private Long id;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private Integer sortOrder;
    private LocalDateTime createTime;
    /** 相对 API 地址，访问时需要携带登录令牌。 */
    private String previewUrl;
}
