package com.recruit.smartrecruit.service;

import com.recruit.smartrecruit.common.PageResult;
import com.recruit.smartrecruit.dto.MyApplicationQueryDTO;
import com.recruit.smartrecruit.vo.MyApplicationDetailVO;
import com.recruit.smartrecruit.vo.MyApplicationListVO;
import com.recruit.smartrecruit.vo.MyApplicationOverviewVO;

/**
 * 求职者「我的投递」服务：分页列表、状态概览、投递详情（含事件流水与面试排期）。
 *
 * <p>所有查询均以当前登录求职者 userId 做数据隔离，只能看到自己的投递。
 */
public interface MyApplicationService {
    /** 按条件分页查询当前用户的投递列表。 */
    PageResult<MyApplicationListVO> page(MyApplicationQueryDTO query, Long userId);

    /** 统计当前用户各状态投递数量概览。 */
    MyApplicationOverviewVO overview(Long userId);

    /** 查询单条投递详情，附带事件流水和面试排期。 */
    MyApplicationDetailVO detail(Long id, Long userId);
}
