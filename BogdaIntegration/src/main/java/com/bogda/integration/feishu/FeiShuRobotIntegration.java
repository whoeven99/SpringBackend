package com.bogda.integration.feishu;

import com.alibaba.fastjson.JSONObject;
import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.integration.http.BaseHttpIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class FeiShuRobotIntegration {

    @Autowired
    private BaseHttpIntegration baseHttpIntegration;
    @Value("${spring.config.activate.on-profile:local}")
    private String env;
    private final static String DEFAULT_WEBHOOK_URL = "https://www.feishu.cn/flow/api/trigger-webhook/09fc4985908785100fac7932ab2a78e6";

    /**
     * 发送飞书机器人消息
     *
     * @param message 消息内容
     * @return 飞书接口响应，失败返回 null
     */
    public String sendMessage(String message) {
        JSONObject body = new JSONObject();
        body.put("message", message);
        body.put("timestamp", Instant.now());
        body.put("source", env);

        String response = baseHttpIntegration.httpPost(DEFAULT_WEBHOOK_URL, body.toJSONString());
        if (response == null) {
            ExceptionReporterHolder.report("FeiShuRobotIntegration.sendMessage",
                    new RuntimeException("FatalException 飞书机器人消息发送失败, message: " + message));
            return null;
        }
        return response;
    }
}
