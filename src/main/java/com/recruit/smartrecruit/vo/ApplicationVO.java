package com.recruit.smartrecruit.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationVO {

    /**
     * 投递ID
     */
    private Long id;

    /**
     * 求职者ID
     */
    private Long userId;

    /**
     * 求职者用户名
     */
    private String username;

    /**
     * 岗位ID
     */
    private Long jobId;

    /**
     * 岗位名称
     */
    private String jobTitle;

    /**
     * 企业名称
     */
    private String companyName;

    /**
     * 简历ID
     */
    private Long resumeId;

    /**
     * 简历名称
     */
    private String resumeTitle;

    /**
     * 投递状态
     */
    private Integer status;

    /**
     * 投递状态文字
     */
    private String statusText;

    /**
     * 投递时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}