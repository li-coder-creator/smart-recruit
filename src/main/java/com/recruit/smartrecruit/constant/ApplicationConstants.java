package com.recruit.smartrecruit.constant;

import java.time.ZoneId;

public final class ApplicationConstants {
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int KEYWORD_MAX_LENGTH = 100;
    public static final String TIME_ZONE = "Asia/Shanghai";
    public static final ZoneId ZONE_ID = ZoneId.of(TIME_ZONE);
    public static final String NOT_FOUND_MESSAGE = "投递不存在或无权访问";
    public static final String INVALID_DATA_MESSAGE = "投递数据异常，请联系管理员";
    public static final String DUPLICATE_APPLICATION_MESSAGE = "请勿重复投递同一岗位";
    public static final String STATUS_CHANGED_MESSAGE = "投递状态已变化，请刷新后重试";
    public static final long INITIAL_INTERVIEW_VERSION = 0;
    public static final String INTERVIEW_NOT_FOUND_MESSAGE = "面试安排不存在或无权访问";
    public static final String INTERVIEW_CONFLICT_MESSAGE = "面试安排已变化，请刷新后重试";
    public static final String APPLICATION_ENDED_CANCEL_REASON = "投递流程已结束，安排失效";

    private ApplicationConstants() {
    }
}
