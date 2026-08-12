package com.recruit.smartrecruit.aspect;

import com.recruit.smartrecruit.annotation.OperationLog;
import com.recruit.smartrecruit.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    /**
     * 环绕通知
     *
     * 拦截所有使用 @OperationLog 的方法
     */
    @Around("@annotation(com.recruit.smartrecruit.annotation.OperationLog)")
    public Object operationLog(ProceedingJoinPoint joinPoint) throws Throwable {

        long startTime = System.currentTimeMillis();

        MethodSignature signature =
                (MethodSignature) joinPoint.getSignature();

        OperationLog operationLog =
                signature.getMethod().getAnnotation(OperationLog.class);

        String operation = operationLog.value();

        log.info("操作开始 operation={}", operation);

        try {

            // 执行真正的业务方法
            Object result = joinPoint.proceed();

            long cost = System.currentTimeMillis() - startTime;

            // 判断是否为统一返回结果
            if (result instanceof Result<?> resultData) {

                if (Integer.valueOf(200).equals(resultData.getCode())) {

                    log.info("操作成功 operation={} cost={}ms",
                            operation,
                            cost);

                } else {

                    log.warn("操作失败 operation={} code={} message={} cost={}ms",
                            operation,
                            resultData.getCode(),
                            resultData.getMessage(),
                            cost);
                }

            } else {

                // 非 Result 类型，暂时按照方法正常返回处理
                log.info("操作成功 operation={} cost={}ms",
                        operation,
                        cost);
            }

            return result;

        } catch (Throwable e) {

            long cost = System.currentTimeMillis() - startTime;

            log.error("操作异常 operation={} cost={}ms",
                    operation,
                    cost,
                    e);

            throw e;
        }
    }
}