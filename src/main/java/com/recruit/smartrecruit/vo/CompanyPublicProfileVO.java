package com.recruit.smartrecruit.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 求职者主页与企业本人预览共用的公开响应，不继承企业管理 VO。 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CompanyPublicProfileVO extends CompanyDisplayVO {
    private LocalDateTime updateTime;
    /** 按 sortOrder、id 排序的办公照片；无照片时为空列表。 */
    private List<CompanyPhotoVO> photos;
}
