package com.recruit.smartrecruit.common;
import lombok.AllArgsConstructor;
import lombok.Data;

import lombok.NoArgsConstructor;
//无参构造函数
@NoArgsConstructor
//全参构造函数
@AllArgsConstructor
@Data
public class Result<T> {
    //状态码
    private Integer code;
    //提示信息
    private String message;
    // 返回数据
    //泛型数据
    private T data;
    // 成功(无数据)
    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }
    // 成功(有数据)
    public static <T> Result<T> success( T data) {
        return new Result<>(200, "success", data);
    }
    // 失败
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }
}
