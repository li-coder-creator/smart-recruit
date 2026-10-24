package com.recruit.smartrecruit.dto;

import com.recruit.smartrecruit.entity.enums.CompanyQualificationType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class CompanyQualificationUploadDTO {
    @NotNull(message = "请选择资质材料类型")
    private CompanyQualificationType qualificationType;
    @NotNull(message = "请选择上传文件")
    private MultipartFile file;
}
