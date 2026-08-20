package com.recruit.smartrecruit.utils;

import java.util.Map;

public class ThreadLocalUtil {

    /**
     * 创建 ThreadLocal 对象
     * 每个线程都有自己独立的一份数据
     */
    private static final ThreadLocal<Map<String, Object>> THREAD_LOCAL =
            new ThreadLocal<>();

    /**
     * 保存数据
     */
    public static void set(Map<String, Object> map) {
        THREAD_LOCAL.set(map);
    }

    /**
     * 获取数据
     */
    public static Map<String, Object> get() {
        return THREAD_LOCAL.get();
    }
    /**
     *
     */
    public static Long getUserId() {
        Map<String, Object> claims = THREAD_LOCAL.get();
        if (claims == null) {
            throw new IllegalStateException("未登录");
        }
        return ((Number) claims.get("id")).longValue();
    }
    /**
     * 删除数据
     */
    public static void remove() {
        THREAD_LOCAL.remove();
    }
}