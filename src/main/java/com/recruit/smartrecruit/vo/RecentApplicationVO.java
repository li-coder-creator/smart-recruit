package com.recruit.smartrecruit.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 最近投递条目。
 * 按 create_time DESC, id DESC 排序，返回有限条数的列表（不含分页总数），无数据时返回空列表。
 * 姓名来源优先使用关联简历姓名，缺失时使用账号昵称，再缺失则显示“未填写姓名”。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecentApplicationVO {

    /**
     * 投递 ID，可用于进入已有投递详情
     */
    private Long applicationId;

    /**
     * 候选人展示姓名
     */
    private String applicantName;

    /**
     * 当前投递状态
     */
    private Integer status;

    /**
     * 状态文字
     */
    private String statusText;

    /**
     * 投递时间
     */
    private LocalDateTime createTime;
}
