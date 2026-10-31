package com.recruit.smartrecruit.entity.enums;

import java.time.LocalDateTime;

/**
 * 投递查询日期范围。
 * 自然日窗口，包含今天；ALL 仍由查询限制不晚于本次服务器时间。
 */
public enum ApplicationDateRange {
    /** 最近7天 */
    LAST_7_DAYS(7),
    /** 最近30天 */
    LAST_30_DAYS(30),
    /** 全部时间，不限起始 */
    ALL(null);

    /**
     * 自然日天数，null 表示不限
     */
    private final Integer calendarDays;

    ApplicationDateRange(Integer calendarDays) {
        this.calendarDays = calendarDays;
    }

    /**
     * 根据当前服务器时间计算查询起始时间，ALL 返回 null。
     */
    public LocalDateTime startAt(LocalDateTime now) {
        return calendarDays == null ? null
                : now.toLocalDate().minusDays(calendarDays - 1L).atStartOfDay();
    }
}
