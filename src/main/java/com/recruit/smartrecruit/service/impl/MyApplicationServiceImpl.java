package com.recruit.smartrecruit.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.recruit.smartrecruit.common.PageResult;
import com.recruit.smartrecruit.constant.ApplicationConstants;
import com.recruit.smartrecruit.dto.ApplicationEventQueryRow;
import com.recruit.smartrecruit.dto.MyApplicationQueryDTO;
import com.recruit.smartrecruit.entity.enums.ApplicationStatus;
import com.recruit.smartrecruit.entity.enums.CompanyStatus;
import com.recruit.smartrecruit.entity.enums.JobStatus;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.MyApplicationMapper;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.service.MyApplicationService;
import com.recruit.smartrecruit.utils.ApplicationInterviewViews;
import com.recruit.smartrecruit.vo.ApplicationEventVO;
import com.recruit.smartrecruit.vo.ApplicationInterviewVO;
import com.recruit.smartrecruit.vo.InterviewChangeVO;
import com.recruit.smartrecruit.vo.MyApplicationDetailVO;
import com.recruit.smartrecruit.vo.MyApplicationListVO;
import com.recruit.smartrecruit.vo.MyApplicationOverviewVO;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 求职者「我的投递」查询服务实现。
 *
 * <p>类级只读事务 + REPEATABLE_READ：同一请求中的普通读取使用一致性快照，
 * 不读取其他事务未提交的中间态；不同 HTTP 请求之间不承诺同一快照。
 *
 * <p>数据隔离：所有 SQL 均绑定 userId 条件，只能查到自己的投递；
 * 历史投递保留非招聘中或关联资料缺失的记录；仅 jobViewable 要求 ACTIVE 岗位和 APPROVED 企业。
 */
@Slf4j
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class MyApplicationServiceImpl implements MyApplicationService {
    private final MyApplicationMapper mapper;
    private final PermissionService permissionService;
    /** 用于校验查询 DTO 的 Bean Validation 执行器。 */
    private final Validator validator;
    /** 统一服务器时钟，与写入侧共用同一时间基准。 */
    private final Clock clock;
    /**
     * 事件 detailJson 的专用读取器：严格模式（未知字段即报错），
     * 写入端序列化什么结构，这里就能在校验失败时及时暴露契约漂移。
     */
    private final ObjectReader interviewChangeReader;

    public MyApplicationServiceImpl(MyApplicationMapper mapper, PermissionService permissionService,
            Validator validator, @Qualifier("applicationClock") Clock clock, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.permissionService = permissionService;
        this.validator = validator;
        this.clock = clock;
        this.interviewChangeReader = objectMapper.readerFor(InterviewChangeVO.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /**
     * 分页查询投递列表。
     *
     * <p>关键字搜索用 {@link #literalLikePattern} 转义后以 #{} 绑定，
     * 用户输入中的 % 和 _ 按字面量匹配、不可操纵 LIKE 模式；
     * finally 中清理 PageHelper 线程变量，防止分页参数泄漏到同线程的后续 SQL。
     */
    @Override
    public PageResult<MyApplicationListVO> page(MyApplicationQueryDTO query, Long userId) {
        permissionService.requireJobSeeker(userId);
        validateQuery(query);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime from = query.getDateRange().startAt(now);
        String keywordPattern = literalLikePattern(query.getKeyword());
        // 权限和输入校验已完成，分页上下文只覆盖紧随其后的列表 SQL。
        try {
            PageHelper.startPage(query.getPage(), query.getPageSize()).setReasonable(false);
            List<MyApplicationListVO> items = mapper.findPage(userId, query, from, now,
                    keywordPattern, JobStatus.ACTIVE, CompanyStatus.APPROVED);
            items.forEach(this::fillStatusText);
            return new PageResult<>(new PageInfo<>(items).getTotal(), items);
        } finally {
            PageHelper.clearPage();
        }
    }

    /**
     * 各状态投递数量概览。
     *
     * <p>防御性一致性校验：分类求和必须等于总数，否则说明库内存在枚举白名单
     * 之外的非法状态码，记录日志并拒绝返回，避免展示一份加起来对不上的统计。
     */
    @Override
    public MyApplicationOverviewVO overview(Long userId) {
        permissionService.requireJobSeeker(userId);
        MyApplicationOverviewVO result = mapper.countOverview(userId, ApplicationStatus.PENDING,
                ApplicationStatus.SCREENING, ApplicationStatus.INTERVIEW,
                ApplicationStatus.HIRED, ApplicationStatus.REJECTED);
        long classified = result.getPendingCount() + result.getScreeningCount()
                + result.getInterviewCount() + result.getHiredCount() + result.getRejectedCount();
        if (classified != result.getTotalCount()) {
            log.error("投递概览存在非法状态，userId={} total={} classified={}",
                    userId, result.getTotalCount(), classified);
            throw new BusinessException(ApplicationConstants.INVALID_DATA_MESSAGE);
        }
        return result;
    }

    /**
     * 投递详情：基础信息 + 事件流水 + 面试排期。
     * 查询同时绑定 userId 做归属校验，查不到（含非本人）统一返回 NOT_FOUND；
     * 响应附带服务器时间与统一时区，供前端按同一基准展示时间状态。
     */
    @Override
    public MyApplicationDetailVO detail(Long id, Long userId) {
        permissionService.requireJobSeeker(userId);
        if (id == null || id <= 0) {
            throw new BusinessException(ApplicationConstants.NOT_FOUND_MESSAGE);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        MyApplicationDetailVO result = mapper.findDetail(id, userId,
                JobStatus.ACTIVE, CompanyStatus.APPROVED);
        if (result == null) {
            throw new BusinessException(ApplicationConstants.NOT_FOUND_MESSAGE);
        }
        fillStatusText(result);
        result.setEvents(mapper.findEvents(id, userId).stream().map(this::toEventVO).toList());
        List<ApplicationInterviewVO> interviews = mapper.findInterviews(id, userId);
        interviews.forEach(interview -> ApplicationInterviewViews.fillTimingStatus(interview, now));
        result.setInterviews(interviews);
        result.setServerTime(now);
        result.setTimeZone(ApplicationConstants.TIME_ZONE);
        return result;
    }

    /** 查询 DTO 校验：Bean Validation + 状态码必须在 ApplicationStatus 合法范围内。 */
    private void validateQuery(MyApplicationQueryDTO query) {
        if (query == null) {
            throw new BusinessException("查询参数不能为空");
        }
        validator.validate(query).stream().findFirst().ifPresent(violation -> {
            throw new BusinessException(violation.getMessage());
        });
        if (query.getStatus() != null) {
            try {
                ApplicationStatus.fromCode(query.getStatus());
            } catch (IllegalArgumentException exception) {
                throw new BusinessException("投递状态必须为0至4的整数");
            }
        }
    }

    /** 把用户输入包装成全字面量匹配的 LIKE 模式，转义符与 SQL 侧 ESCAPE 约定一致。 */
    private String literalLikePattern(String keyword) {
        if (keyword == null) {
            return null;
        }
        // 与 SQL ESCAPE '!' 配套；所有内容仍通过 #{} 绑定。
        return "%" + keyword.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";
    }

    /** 用枚举描述回填展示文案；REJECTED 对求职者展示为更柔和的「未通过」。 */
    private void fillStatusText(MyApplicationListVO item) {
        item.setStatusText(statusText(item.getStatus(), item.getId()));
    }

    /**
     * 状态码 → 展示文案，带数据合法性兼底：非法状态码记录错误日志并拒绝服务，
     * 不把脏数据静默呈现给前端。 resourceId 用于定位问题行（投递 ID 或事件 ID）。
     */
    private String statusText(Integer code, Long resourceId) {
        try {
            ApplicationStatus status = ApplicationStatus.fromCode(code);
            return status == ApplicationStatus.REJECTED ? "未通过" : status.getDescription();
        } catch (IllegalArgumentException exception) {
            log.error("投递状态数据异常，resourceId={} status={}", resourceId, code);
            throw new BusinessException(ApplicationConstants.INVALID_DATA_MESSAGE);
        }
    }

    /**
     * 查询行 → 事件 VO：按事件类型分支校验字段组合并生成中文摘要文案。
     *
     * <p>原则：展示层不信任库内数据，每种事件类型都有对应的字段约束
     * （如 APPLIED 必须无前置状态且 toStatus=PENDING），不满足即视为数据异常；
     * 面试类事件的 detailJson 用严格模式反序列化，失败时不记录原始 JSON
     * （可能含链接/地址/备注等敏感内容），只记事件 ID。
     */
    private ApplicationEventVO toEventVO(ApplicationEventQueryRow row) {
        if (row.getEventType() == null || row.getActorType() == null || row.getOccurredAt() == null) {
            throw invalidEvent(row.getId());
        }
        ApplicationEventVO event = new ApplicationEventVO();
        event.setId(row.getId());
        event.setEventType(row.getEventType());
        event.setActorType(row.getActorType());
        event.setOccurredAt(row.getOccurredAt());
        switch (row.getEventType()) {
            case APPLIED -> {
                if (row.getFromStatus() != null
                        || !ApplicationStatus.PENDING.getCode().equals(row.getToStatus())) {
                    throw invalidEvent(row.getId());
                }
                event.setToStatus(row.getToStatus());
                event.setSummary("投递成功");
            }
            case STATUS_CHANGED -> {
                String from = statusText(row.getFromStatus(), row.getId());
                String to = statusText(row.getToStatus(), row.getId());
                event.setFromStatus(row.getFromStatus());
                event.setToStatus(row.getToStatus());
                event.setSummary("投递状态由“" + from + "”变为“" + to + "”");
            }
            case INTERVIEW_SCHEDULED, INTERVIEW_RESCHEDULED, INTERVIEW_CANCELLED -> {
                if (row.getInterviewId() == null || row.getDetailJson() == null) {
                    throw invalidEvent(row.getId());
                }
                event.setInterviewId(row.getInterviewId());
                try {
                    InterviewChangeVO change = interviewChangeReader.readValue(row.getDetailJson());
                    if (change == null) {
                        throw invalidEvent(row.getId());
                    }
                    event.setInterviewChange(change);
                } catch (JsonProcessingException exception) {
                    // 不记录包含链接、地址或备注的原始 JSON / Jackson 异常信息。
                    throw invalidEvent(row.getId());
                }
                event.setSummary(switch (row.getEventType()) {
                    case INTERVIEW_SCHEDULED -> "企业已安排面试";
                    case INTERVIEW_RESCHEDULED -> "企业已调整面试安排";
                    case INTERVIEW_CANCELLED -> "面试安排已取消";
                    default -> throw invalidEvent(row.getId());
                });
            }
        }
        return event;
    }

    /** 统一的「事件数据异常」构造器：记录错误日志后返回对外模糊化的业务异常。 */
    private BusinessException invalidEvent(Long eventId) {
        log.error("投递事件数据异常，eventId={}", eventId);
        return new BusinessException(ApplicationConstants.INVALID_DATA_MESSAGE);
    }

}
