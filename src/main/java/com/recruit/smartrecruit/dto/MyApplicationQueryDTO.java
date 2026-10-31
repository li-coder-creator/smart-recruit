package com.recruit.smartrecruit.dto;

import com.recruit.smartrecruit.constant.ApplicationConstants;
import com.recruit.smartrecruit.constant.JobValidationConstants;
import com.recruit.smartrecruit.entity.enums.ApplicationDateRange;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 我的投递列表查询参数。
 * 用于求职者分页查询自己的投递记录，支持状态、关键词与日期范围过滤。
 */
@Data
public class MyApplicationQueryDTO {
    /**
     * 页码，从1开始，默认第1页
     */
    @NotNull(message = "页码不能为空")
    @Min(value = JobValidationConstants.MIN_PAGE_NUMBER, message = "页码必须大于等于1")
    private Integer page = ApplicationConstants.DEFAULT_PAGE;

    /**
     * 每页条数，最大100，默认10（ApplicationConstants.DEFAULT_PAGE_SIZE）。
     */
    @NotNull(message = "每页条数不能为空")
    @Min(value = JobValidationConstants.MIN_PAGE_SIZE, message = "每页条数必须大于等于1")
    @Max(value = JobValidationConstants.MAX_PAGE_SIZE, message = "每页条数不能超过100")
    private Integer pageSize = ApplicationConstants.DEFAULT_PAGE_SIZE;

    /**
     * 投递状态过滤，为空表示全部。
     * 合法状态由 Service 使用 ApplicationStatus 认定，避免重复维护数字区间。
     */
    private Integer status;

    /**
     * 关键词，匹配岗位/企业等名称，最长100字符，提交前自动去除首尾空白
     */
    @Size(max = ApplicationConstants.KEYWORD_MAX_LENGTH, message = "关键词不能超过100字符")
    private String keyword;

    /**
     * 日期范围过滤，默认最近30天
     */
    @NotNull(message = "日期范围不能为空")
    private ApplicationDateRange dateRange = ApplicationDateRange.LAST_30_DAYS;

    public void setKeyword(String keyword) {
        this.keyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
    }
}
