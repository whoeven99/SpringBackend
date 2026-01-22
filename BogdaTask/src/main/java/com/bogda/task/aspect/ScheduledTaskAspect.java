package com.bogda.task.aspect;

import com.bogda.task.annotation.EnableScheduledTask;
import org.springframework.beans.factory.annotation.Value;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 定时任务AOP切面
 * 
 * 功能：
 * 1. 拦截所有带 @Scheduled 注解的方法
 * 2. 如果方法上有 @EnableScheduledTask 注解，则总是执行
 * 3. 如果方法上没有 @EnableScheduledTask 注解：
 *    - local环境：不执行
 *    - test/prod环境：执行
 */
@Aspect
@Component
public class ScheduledTaskAspect {
    
    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskAspect.class);

    @Value("${spring.config.activate.on-profile:local}")
    private String env;

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object aroundScheduledMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // 检查是否有 @EnableScheduledTask 注解
        boolean hasEnableAnnotation = AnnotatedElementUtils.hasAnnotation(method, EnableScheduledTask.class);
        
        if (hasEnableAnnotation) {
            // 有注解，总是执行
            log.debug("执行定时任务: {}.{} (已启用)", 
                    joinPoint.getTarget().getClass().getSimpleName(), 
                    method.getName());
            return joinPoint.proceed();
        }
        
        // 没有注解，根据环境判断
        // 直接获取SPRING_PROFILES_ACTIVE环境变量或配置
        if ("test".equalsIgnoreCase(env) || "prod".equalsIgnoreCase(env)) {
            log.debug("执行定时任务: {}.{} (云上环境: {})",
                    joinPoint.getTarget().getClass().getSimpleName(),
                    method.getName(),
                    env);
            return joinPoint.proceed();
        } else {
            // local环境或其他环境，不执行
            log.debug("跳过定时任务: {}.{} (本地环境)",
                    joinPoint.getTarget().getClass().getSimpleName(), 
                    method.getName());
            return null;
        }
    }
}

