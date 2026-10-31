package com.recruit.smartrecruit.utils;

import com.recruit.smartrecruit.constant.ApplicationConstants;
import com.recruit.smartrecruit.entity.ApplicationInterview;
import com.recruit.smartrecruit.entity.enums.InterviewStatus;
import com.recruit.smartrecruit.entity.enums.InterviewTimingStatus;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.vo.ApplicationInterviewVO;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;

/** 只组装公开 VO，时间状态由请求取得的同一个 now 派生。 */
public final class ApplicationInterviewViews {
    private ApplicationInterviewViews() {
    }

    public static ApplicationInterviewVO toVO(ApplicationInterview entity, LocalDateTime now) {
        ApplicationInterviewVO vo = new ApplicationInterviewVO();
        // 目标 VO 没有 createdBy / updatedBy / cancelSource 等内部字段。
        BeanUtils.copyProperties(entity, vo);
        fillTimingStatus(vo, now);
        return vo;
    }

    public static void fillTimingStatus(ApplicationInterviewVO vo, LocalDateTime now) {
        if (vo.getStatus() == null || vo.getStartTime() == null || vo.getEndTime() == null
                || !vo.getEndTime().isAfter(vo.getStartTime())) {
            throw new BusinessException(ApplicationConstants.INVALID_DATA_MESSAGE);
        }
        if (vo.getStatus() == InterviewStatus.CANCELLED) {
            vo.setTimingStatus(InterviewTimingStatus.CANCELLED);
        } else if (now.isBefore(vo.getStartTime())) {
            vo.setTimingStatus(InterviewTimingStatus.UPCOMING);
        } else if (now.isBefore(vo.getEndTime())) {
            vo.setTimingStatus(InterviewTimingStatus.ONGOING);
        } else {
            vo.setTimingStatus(InterviewTimingStatus.EXPIRED);
        }
    }
}
