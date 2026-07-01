package com.bogda.task.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查接口。
 * <p>Azure App Service for Containers 会周期性探测容器的 HTTP 端口，
 * 探测失败会判定容器不健康并回收重启。该接口提供轻量存活探针，
 * 仅返回进程存活状态，不依赖 DB / Redis 等下游，避免下游抖动误伤 task 进程。</p>
 */
@RestController
public class HealthController {

    private static final String SERVER_START_TIME =
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    @GetMapping({"/", "/health"})
    public Map<String, String> health() {
        Map<String, String> info = new HashMap<>();
        info.put("service", "bogda-task");
        info.put("status", "UP");
        info.put("startTime", SERVER_START_TIME);
        info.put("currentTime",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return info;
    }
}
