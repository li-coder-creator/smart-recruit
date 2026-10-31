package com.recruit.smartrecruit.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 下一场面试响应包装。
 * 统一携带服务器时间与时区，用于前端根据时间派生展示状态。
 */
@Data
public class MyNextInterviewVO {
    /**
     * 服务器统一时间，由 Service 一次性取得，VO 不自行读时间
     */
    private LocalDateTime serverTime;
    /**
     * 时区标识，按约定为 Asia/Shanghai
     */
    private String timeZone;
    /**
     * 下一场即将到来的面试，没有时为空
     */
    private MyNextInterviewItemVO interview;
}
