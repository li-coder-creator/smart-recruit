package com.recruit.smartrecruit.exception;

import com.recruit.smartrecruit.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

//全局异常处理
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    //文件上传大小超出异常处理
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleUploadSize(MaxUploadSizeExceededException exception) {
        return Result.error("上传文件不能超过10MB，请分次上传");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public Result<Void> handleMissingFile(MissingServletRequestPartException exception) {
        return Result.error("请选择上传文件");
    }

    //业务异常处理
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常：{}",e.getMessage());
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
        log.warn("参数校验异常：{}",msg);
        return Result.error(msg);
    }
    /** DTO枚举、显式非法null、未知字段和JSON语法错误不伪装成系统故障。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return Result.error("请求参数格式不正确，请检查字段名称、枚举代码和空值");
    }

    /** 查询参数枚举或数字非法时返回参数错误，不归类为未知系统异常。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleQueryTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return Result.error("参数" + exception.getName() + "格式不正确");
    }

    //处理其他异常
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统发生未知异常", e);
        return Result.error("系统异常，请联系管理员");
    }




}
