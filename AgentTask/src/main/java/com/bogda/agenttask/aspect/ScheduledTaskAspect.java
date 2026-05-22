package com.bogda.agenttask.aspect;

import com.bogda.agenttask.annotation.EnableScheduledTask;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 与 BogdaTask {@code ScheduledTaskAspect} 一致；额外将 {@code render} 视为云上环境以便 Render 部署。
 */
@Aspect
@Component
public class ScheduledTaskAspect {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduledTaskAspect.class);

    @Value("${spring.profiles.active:${ApplicationEnv:${spring.config.activate.on-profile:local}}}")
    private String env;

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object aroundScheduledMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        boolean hasEnableAnnotation = AnnotatedElementUtils.hasAnnotation(method, EnableScheduledTask.class);

        if (hasEnableAnnotation) {
            LOG.debug("执行定时任务: {} (已启用)", method.getName());
            return joinPoint.proceed();
        }

        if (isCloudEnv(env)) {
            LOG.debug("执行定时任务: {} (云上环境: {})", method.getName(), env);
            return joinPoint.proceed();
        }

        LOG.debug("跳过定时任务: {} (本地环境)", method.getName());
        return null;
    }

    private boolean isCloudEnv(String rawEnv) {
        if (rawEnv == null || rawEnv.trim().isEmpty()) {
            return false;
        }
        return Arrays.stream(rawEnv.split(","))
                .map(String::trim)
                .anyMatch(v -> "test".equalsIgnoreCase(v)
                        || "prod".equalsIgnoreCase(v)
                        || "render".equalsIgnoreCase(v));
    }
}
