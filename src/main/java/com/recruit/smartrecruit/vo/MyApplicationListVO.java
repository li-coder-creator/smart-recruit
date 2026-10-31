package com.recruit.smartrecruit.vo;

import com.recruit.smartrecruit.entity.enums.JobSalaryUnit;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 求职者本人投递列表的展示白名单。
 * 岗位、企业和简历字段取当前关联资料，不表示投递时的历史快照。
 */
@Data
@NoArgsConstructor
public class MyApplicationListVO {
    /** 投递 ID。 */
    private Long id;
    /** 原投递保存的岗位 ID，关联岗位缺失时仍保留。 */
    private Long jobId;
    /** 当前岗位所属企业 ID，岗位缺失时可以为空。 */
    private Long companyId;
    /** 当前关联岗位名称，岗位缺失时可以为空。 */
    private String jobTitle;
    /** 当前关联企业名称，岗位缺失时可以为空。 */
    private String companyName;
    /** 没有 Logo 时为空，由前端提供中性占位。 */
    private String companyLogo;
    /** 岗位所在城市。 */
    private String city;
    /** 当前用工类型，不据此推断校招等招聘类型。 */
    private String employment;
    /** 薪资下限，与 salaryMax、salaryUnit 配合展示。 */
    private Integer salaryMin;
    /** 薪资上限，与 salaryMin、salaryUnit 配合展示。 */
    private Integer salaryMax;
    /** 薪资单位（如月薪、年薪）。 */
    private JobSalaryUnit salaryUnit;
    /** 岗位信息缺失时可以为空，不能据此假定薪资面议。 */
    private Boolean negotiable;
    /** 当前岗位状态，与投递状态 status 分开。 */
    private Integer jobStatus;
    /** 当前关联岗位是否存在。 */
    private Boolean jobAvailable;
    /** 当前是否允许从求职者岗位入口查看，已结束岗位可以存在但不可跳转。 */
    private Boolean jobViewable;
    /** 当前关联企业是否存在；审核是否通过另参与 jobViewable 判断。 */
    private Boolean companyAvailable;
    /** 原投递保存的简历 ID，简历删除后仍保留。 */
    private Long resumeId;
    /** 当前关联简历名称，简历缺失时可以为空。 */
    private String resumeTitle;
    /** 当前简历存在，且仍属于这条投递的求职者。 */
    private Boolean resumeAvailable;
    /** 投递状态，沿用 ApplicationStatus 的现有代码。 */
    private Integer status;
    /** 求职者展示文案，REJECTED 显示为“未通过”。 */
    private String statusText;
    /** 投递创建时间。 */
    private LocalDateTime createTime;
    /** 投递最后更新时间。 */
    private LocalDateTime updateTime;
}
