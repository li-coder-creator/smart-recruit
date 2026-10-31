package com.recruit.smartrecruit.entity.enums;

/**
 * 面试取消来源。
 * 内部取消来源，不接收客户端指定。
 */
public enum InterviewCancelSource {
    /** 企业主动取消 */
    COMPANY,
    /** 因投递状态变更联动取消 */
    APPLICATION_STATUS
}
