package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.Resumebasic;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;
import com.recruit.smartrecruit.entity.enums.CareerStage;
import com.recruit.smartrecruit.entity.enums.FormalExperienceStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 独立响应契约；仅从已鉴权读取的实体复制，不执行查询或推断未知信息。 */
@Data
@NoArgsConstructor
public class ResumeBasicVO {
    private Long id;
    private Long userId;
    private String title;
    private String description;
    private String fileUrl;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String realName;
    private String gender;
    private LocalDate birthday;
    private String email;
    private String phone;
    private String currentCity;
    private String politicsStatus;
    private String headline;
    private String summary;
    private Boolean isDefault;
    private Boolean isFavorite;
    private Integer completionRate;
    private CareerStage careerStage;
    private FormalExperienceStatus formalExperienceStatus;

    public ResumeBasicVO(Resumebasic source) {
        BeanUtils.copyProperties(source, this);
    }
}
