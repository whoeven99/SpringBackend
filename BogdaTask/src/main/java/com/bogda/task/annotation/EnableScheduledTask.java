package com.bogda.task.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 启用定时任务注解
 * 
 * 使用说明：
 * 1. 在本地环境，默认所有定时任务不执行
 * 2. 在test/prod环境，默认所有定时任务执行
 * 3. 如果方法上添加了此注解，则无论什么环境都会执行
 * 
 * 示例：
 * <pre>
 * {@code
 * @Scheduled(fixedRate = 20000)
 * @EnableScheduledTask
 * public void myTask() {
 *     // 此任务在所有环境都会执行
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnableScheduledTask {
}

