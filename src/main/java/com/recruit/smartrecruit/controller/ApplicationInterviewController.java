package com.recruit.smartrecruit.controller;

import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.dto.ApplicationInterviewCancelDTO;
import com.recruit.smartrecruit.dto.ApplicationInterviewCreateDTO;
import com.recruit.smartrecruit.dto.ApplicationInterviewUpdateDTO;
import com.recruit.smartrecruit.service.ApplicationInterviewService;
import com.recruit.smartrecruit.utils.ThreadLocalUtil;
import com.recruit.smartrecruit.vo.ApplicationInterviewListVO;
import com.recruit.smartrecruit.vo.ApplicationInterviewVO;
import com.recruit.smartrecruit.vo.MyNextInterviewVO;
import jakarta.validation.Valid;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/application")
public class ApplicationInterviewController {
    private final ApplicationInterviewService service;

    public ApplicationInterviewController(ApplicationInterviewService service) {
        this.service = service;
    }

    @GetMapping("/{id}/interviews")
    public Result<ApplicationInterviewListVO> list(@PathVariable("id") Long id) {
        return Result.success(service.list(id, ThreadLocalUtil.getUserId()));
    }

    @PostMapping("/{id}/interviews")
    public Result<ApplicationInterviewVO> create(@PathVariable("id") Long id,
            @Valid @RequestBody ApplicationInterviewCreateDTO dto) {
        return Result.success(service.create(id, dto, ThreadLocalUtil.getUserId()));
    }

    @PutMapping("/{id}/interviews/{interviewId}")
    public Result<ApplicationInterviewVO> reschedule(@PathVariable("id") Long id,
            @PathVariable("interviewId") Long interviewId,
            @Valid @RequestBody ApplicationInterviewUpdateDTO dto) {
        return Result.success(service.reschedule(id, interviewId, dto, ThreadLocalUtil.getUserId()));
    }

    @PutMapping("/{id}/interviews/{interviewId}/cancel")
    public Result<Void> cancel(@PathVariable("id") Long id, @PathVariable("interviewId") Long interviewId,
            @Valid @RequestBody ApplicationInterviewCancelDTO dto) {
        service.cancel(id, interviewId, dto, ThreadLocalUtil.getUserId());
        return Result.success();
    }

    @GetMapping("/mine/interviews/next")
    public Result<MyNextInterviewVO> next() {
        return Result.success(service.next(ThreadLocalUtil.getUserId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> invalidBody(HttpMessageNotReadableException exception) {
        return Result.error("请求格式错误，请检查时间、枚举和参数字段");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> invalidId(MethodArgumentTypeMismatchException exception) {
        return Result.error("投递和面试ID必须为正整数");
    }
}
