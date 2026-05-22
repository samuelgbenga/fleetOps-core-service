package com.fleetops.core.shared.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    @Around("execution(* com.fleetops.core.module.*.service.impl.*.*(..))")
    public Object logServiceCall(ProceedingJoinPoint pjp) throws Throwable {
        String className = pjp.getTarget().getClass().getSimpleName();
        String methodName = pjp.getSignature().getName();
        String args = Arrays.stream(pjp.getArgs())
                .map(this::maskSensitive)
                .collect(Collectors.joining(", "));

        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            long elapsed = System.currentTimeMillis() - start;
            log.info("[{}#{}] args=[{}] duration={}ms", className, methodName, args, elapsed);
            return result;
        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[{}#{}] args=[{}] duration={}ms FAILED: {}",
                    className, methodName, args, elapsed, ex.getMessage(), ex);
            throw ex;
        }
    }

    private String maskSensitive(Object arg) {
        if (arg == null) return "null";
        String str = arg.toString();
        // Mask fields that look like password/token/secret values
        if (str.toLowerCase().contains("password") || str.toLowerCase().contains("token")
                || str.toLowerCase().contains("secret")) {
            return "[MASKED]";
        }
        return str;
    }
}
