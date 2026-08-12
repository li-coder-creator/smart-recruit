package com.recruit.smartrecruit.interceptor;

import com.recruit.smartrecruit.utils.JwtUtil;
import com.recruit.smartrecruit.utils.ThreadLocalUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.slf4j.MDC;

import java.util.Map;

@Component
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //令牌验证
        //获取令牌
        String token = request.getHeader("Authorization");

        if (token == null || !token.startsWith("Bearer ")) {
            response.setStatus(401);
            return false;
        }

        token = token.substring(7);
       //验证令牌
        try {
            Map<String,Object> claims= JwtUtil.parseToken(token);
            //保存到ThreadLocal
            ThreadLocalUtil.set(claims);
            // 保存 userId 到 MDC
            MDC.put("userId", String.valueOf(claims.get("id")));
           //放行
            return true;
        } catch (Exception e) {
           //http响应状态码为401
            response.setStatus(401);
            //不放行
            return false;
        }
    }

    //请求结束后的操作
    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) throws Exception {
        // 清除 MDC 中的 userId
        MDC.remove("userId");
        // 请求结束，清除 ThreadLocal
        ThreadLocalUtil.remove();
    }
}
