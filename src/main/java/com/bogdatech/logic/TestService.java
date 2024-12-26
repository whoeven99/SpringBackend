package com.bogdatech.logic;

import com.bogdatech.config.TaskSchedulerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledFuture;

@Component
public class TestService {

    @Autowired
    private TaskSchedulerConfig taskSchedulerConfig;
    private ScheduledFuture<?> scheduledFuture; // 用来保存正在运行的任务

    // 启动异步翻译任务
    @Async
    public void clickTranslate() {
        System.out.println("翻译任务正在运行...");
        try {
            // 模拟翻译任务，假设这个任务运行时间很长
            while (true) {
                // 模拟翻译过程
                System.out.println("翻译中...");
                Thread.sleep(1000); // 每秒钟进行一次翻译
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("翻译任务真的被中断");
        }
    }

    // 启动定时任务
    public void startTask() {
        TaskScheduler scheduler = taskSchedulerConfig.taskScheduler();
        scheduledFuture = scheduler.schedule(this::clickTranslate, new java.util.Date());  // 调用定时任务
    }

    // 停止定时任务
    public void stopTask() {
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(true); // 取消任务并中断
            System.out.println("定时任务已停止");
        }
    }
}

