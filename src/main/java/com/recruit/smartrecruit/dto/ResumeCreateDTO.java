package com.recruit.smartrecruit.dto;

import com.recruit.smartrecruit.constant.ResumeValidationConstants;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeCreateDTO {
    @NotBlank(message = "简历名称不能为空")
    @Size(max = ResumeValidationConstants.RESUME_TITLE_MAX_LENGTH, message = "简历名称长度不能超过15个字")
    private String title;
    @Size(max = ResumeValidationConstants.REAL_NAME_MAX_LENGTH, message = "姓名长度不能超过20个字")
    private String realName;
    private String gender;
    @PastOrPresent(message = "出生日期不能是未来日期")
    private LocalDate birthday;
    @Email(message = "邮箱格式不正确")
    private String email;
    @Pattern(
            message = "电话格式不正确",
            regexp = "^1[3-9]\\d{9}$"
    )
    private String phone;
    @Size(max = ResumeValidationConstants.CITY_MAX_LENGTH, message = "城市长度不能超过50个字")
    private String currentCity;
    @Size(max = ResumeValidationConstants.POLITICS_STATUS_MAX_LENGTH, message = "政治面貌长度不能超过20个字")
    private String politicsStatus;
    @Size(max = ResumeValidationConstants.HEADLINE_MAX_LENGTH, message = "个人优势一句话长度不能超过50个字")
    private String headline;
    @Size(max = ResumeValidationConstants.SUMMARY_MAX_LENGTH, message = "个人简介长度不能超过150个字")
    private String summary;
}
