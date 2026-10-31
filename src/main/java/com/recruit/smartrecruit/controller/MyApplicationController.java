package com.recruit.smartrecruit.controller;

import com.recruit.smartrecruit.common.PageResult;
import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.dto.MyApplicationQueryDTO;
import com.recruit.smartrecruit.service.MyApplicationService;
import com.recruit.smartrecruit.utils.ThreadLocalUtil;
import com.recruit.smartrecruit.vo.MyApplicationDetailVO;
import com.recruit.smartrecruit.vo.MyApplicationListVO;
import com.recruit.smartrecruit.vo.MyApplicationOverviewVO;
import jakarta.validation.Valid;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/application/mine")
public class MyApplicationController {
    private final MyApplicationService myApplicationService;

    public MyApplicationController(MyApplicationService myApplicationService) {
        this.myApplicationService = myApplicationService;
    }

    @InitBinder("query")
    public void bindQuery(WebDataBinder binder) {
        binder.setAllowedFields("page", "pageSize", "status", "keyword", "dateRange");
    }

    @GetMapping("/page")
    public Result<PageResult<MyApplicationListVO>> page(
            @Valid @ModelAttribute("query") MyApplicationQueryDTO query) {
        return Result.success(myApplicationService.page(query, ThreadLocalUtil.getUserId()));
    }

    @GetMapping("/overview")
    public Result<MyApplicationOverviewVO> overview() {
        return Result.success(myApplicationService.overview(ThreadLocalUtil.getUserId()));
    }

    @GetMapping("/{id}")
    public Result<MyApplicationDetailVO> detail(@PathVariable("id") Long id) {
        return Result.success(myApplicationService.detail(id, ThreadLocalUtil.getUserId()));
    }

    // 仅处理本查询 Controller 的绑定错误，不改变旧接口的异常契约。
    @ExceptionHandler(BindException.class)
    public Result<Void> invalidQuery(BindException exception) {
        FieldError error = exception.getBindingResult().getFieldError();
        if (error == null) {
            return Result.error("查询参数错误");
        }
        if (!error.isBindingFailure()) {
            return Result.error(error.getDefaultMessage());
        }
        String message = switch (error.getField()) {
            case "page" -> "页码必须为有效整数";
            case "pageSize" -> "每页条数必须为有效整数";
            case "status" -> "投递状态必须为0至4的整数";
            case "dateRange" -> "日期范围只支持LAST_7_DAYS、LAST_30_DAYS、ALL";
            default -> "查询参数错误";
        };
        return Result.error(message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> invalidId(MethodArgumentTypeMismatchException exception) {
        return Result.error("投递ID必须为正整数");
    }
}
