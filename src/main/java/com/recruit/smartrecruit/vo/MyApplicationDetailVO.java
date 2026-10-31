package com.recruit.smartrecruit.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 求职者本人投递详情，复用列表展示字段并补充真实事件与面试安排。 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MyApplicationDetailVO extends MyApplicationListVO {
    /**
     * 是否从投递创建起就完整记录事件。
     * 旧记录即使已有后续事件，也不能据此设为 true。
     */
    private Boolean historyComplete;
    /** 已记录的真实事件，按 occurredAt、id 倒序，不补造跳过的阶段。 */
    private List<ApplicationEventVO> events = new ArrayList<>();
    /** 本条投递的面试场次，按 startTime、id 倒序。 */
    private List<ApplicationInterviewVO> interviews = new ArrayList<>();
    /** 由 Service 为同一次响应统一取得，VO 不自行读取当前时间。 */
    private LocalDateTime serverTime;
    /** 由 Service 按约定设置为 Asia/Shanghai。 */
    private String timeZone;
}
