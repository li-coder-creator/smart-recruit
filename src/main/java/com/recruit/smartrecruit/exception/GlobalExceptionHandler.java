package com.recruit.smartrecruit.exception;

import com.recruit.smartrecruit.common.Result;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

//全局异常处理
@RestControllerAdvice
public class GlobalExceptionHandler {
    //业务异常处理
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
       return Result.error(e.getMessage());
    }
    //参数校验异常处理
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e){

        String msg = "参数错误";

        if (e.getBindingResult().getFieldError() != null) {
            msg = e.getBindingResult()
                    .getFieldError()
                    .getDefaultMessage();
        }

        return Result.error(msg);
    }
    //处理其他异常
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        e.printStackTrace();
        return Result.error("系统异常，请联系管理员");
    }




}
