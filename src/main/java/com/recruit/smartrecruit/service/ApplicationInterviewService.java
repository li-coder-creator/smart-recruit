package com.recruit.smartrecruit.service;

import com.recruit.smartrecruit.dto.ApplicationInterviewCancelDTO;
import com.recruit.smartrecruit.dto.ApplicationInterviewCreateDTO;
import com.recruit.smartrecruit.dto.ApplicationInterviewUpdateDTO;
import com.recruit.smartrecruit.vo.ApplicationInterviewListVO;
import com.recruit.smartrecruit.vo.ApplicationInterviewVO;
import com.recruit.smartrecruit.vo.MyNextInterviewVO;

/**
 * 面试安排服务：企业侧对某条投递的面试排期进行增删改查，
 * 以及求职者侧查询最近一场待参加面试。
 *
 * <p>企业侧写操作均要求：投递属于当前审核通过的企业、投递处于 INTERVIEW 状态，
 * 并以 version 乐观锁防止并发改约/取消互相覆盖。
 */
public interface ApplicationInterviewService {
    /** 列出某条投递的全部面试排期，附带服务器时间与统一时区。 */
    ApplicationInterviewListVO list(Long applicationId, Long userId);

    /** 为投递创建新的面试排期；同一投递同时只允许存在一条未结束的排期。 */
    ApplicationInterviewVO create(Long applicationId, ApplicationInterviewCreateDTO dto, Long userId);

    /** 改约未开始的面试：更新时间和方式信息，并记录改约前后对比事件。 */
    ApplicationInterviewVO reschedule(Long applicationId, Long interviewId,
            ApplicationInterviewUpdateDTO dto, Long userId);

    /** 企业主动取消未结束的面试，需填写取消原因并通过版本校验。 */
    void cancel(Long applicationId, Long interviewId, ApplicationInterviewCancelDTO dto, Long userId);

    /** 求职者首页卡片用：查询当前用户最近一场待参加的面试，无则为空。 */
    MyNextInterviewVO next(Long userId);
}
