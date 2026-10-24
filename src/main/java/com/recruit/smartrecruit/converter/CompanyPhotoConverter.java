package com.recruit.smartrecruit.converter;

import com.recruit.smartrecruit.entity.CompanyPhoto;
import com.recruit.smartrecruit.vo.CompanyPhotoVO;

/** 管理列表和主页共用的照片展示白名单，不暴露存储引用。 */
public final class CompanyPhotoConverter {
    private CompanyPhotoConverter() {
    }

    public static CompanyPhotoVO toVO(CompanyPhoto photo) {
        CompanyPhotoVO vo = new CompanyPhotoVO();
        vo.setId(photo.getId());
        vo.setOriginalFilename(photo.getOriginalFilename());
        vo.setContentType(photo.getContentType());
        vo.setFileSize(photo.getFileSize());
        vo.setSortOrder(photo.getSortOrder());
        vo.setCreateTime(photo.getCreateTime());
        vo.setPreviewUrl("/company/photos/" + photo.getId() + "/file");
        return vo;
    }
}
