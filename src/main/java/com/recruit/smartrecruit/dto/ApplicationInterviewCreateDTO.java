package com.recruit.smartrecruit.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.recruit.smartrecruit.constant.ApplicationValidationConstants;
import com.recruit.smartrecruit.entity.enums.InterviewMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试创建请求参数。
 * 用于企业为某个投递安排新面试，包含时间、方式与地点/链接等信息。
 */
@Data
public class ApplicationInterviewCreateDTO {
    /**
     * 面试开始时间，必填，格式为ISO_LOCAL_DATE_TIME
     */
    @NotNull(message = "面试开始时间不能为空")
    @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ss[.SSS]", lenient = OptBoolean.FALSE)
    private LocalDateTime startTime;
    /**
     * 面试结束时间，必填，必须晚于开始时间
     */
    @NotNull(message = "面试结束时间不能为空")
    @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ss[.SSS]", lenient = OptBoolean.FALSE)
    private LocalDateTime endTime;
    /**
     * 面试方式，仅支持 ONLINE（线上）或 OFFLINE（线下）。
     */
    @NotNull(message = "面试方式不能为空")
    private InterviewMode mode;
    /**
     * 面试平台名称，线上面试时使用，最长50字符
     */
    @Size(max = ApplicationValidationConstants.PLATFORM_MAX_LENGTH, message = "平台不能超过50字符")
    private String platform;
    /**
     * 会议链接，线上面试时使用，最长1000字符
     */
    @Size(max = ApplicationValidationConstants.MEETING_URL_MAX_LENGTH, message = "会议链接不能超过1000字符")
    private String meetingUrl;
    /**
     * 面试地址，线下面试时使用，最长255字符
     */
    @Size(max = ApplicationValidationConstants.ADDRESS_MAX_LENGTH, message = "面试地址不能超过255字符")
    private String address;
    /**
     * 给求职者的补充说明，最长500字符
     */
    @Size(max = ApplicationValidationConstants.CANDIDATE_NOTE_MAX_LENGTH, message = "求职者说明不能超过500字符")
    private String candidateNote;

    public void setPlatform(String value) { platform = normalize(value); }
    public void setMeetingUrl(String value) { meetingUrl = normalize(value); }
    public void setAddress(String value) { address = normalize(value); }
    public void setCandidateNote(String value) { candidateNote = normalize(value); }

    /**
     * 将空白字符串归一化为 null，避免存入多余空值
     */
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * 拒绝未定义的额外字段，保证入参契约严格
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("不支持的面试参数字段");
    }
}
