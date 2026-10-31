package com.recruit.smartrecruit.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 面试安排列表响应包装。
 * 统一携带服务器时间与时区，面试列表项仅本人求职者或所属企业可读。
 */
@Data
public class ApplicationInterviewListVO {
    /**
     * 关联投递ID
     */
    private Long applicationId;
    /**
     * 服务器统一时间，由 Service 一次性取得，VO 不自行读时间
     */
    private LocalDateTime serverTime;
    /**
     * 时区标识，按约定为 Asia/Shanghai
     */
    private String timeZone;
    /**
     * 面试场次列表，按 startTime、id 倒序
     */
    private List<ApplicationInterviewVO> items = new ArrayList<>();
}
