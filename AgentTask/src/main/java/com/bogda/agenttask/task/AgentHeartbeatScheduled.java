package com.bogda.agenttask.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 示例定时任务：可按业务替换为 Agent 轮询、补偿任务等。
 */
@Component
public class AgentHeartbeatScheduled {

    private static final Logger LOG = LoggerFactory.getLogger(AgentHeartbeatScheduled.class);

    @Scheduled(fixedDelayString = "${agent.task.heartbeat-ms:20000}")
    public void heartbeat() {
        LOG.info("AgentTask heartbeat");
    }
}
