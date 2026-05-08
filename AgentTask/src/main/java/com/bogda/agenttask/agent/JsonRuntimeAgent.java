package com.bogda.agenttask.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface JsonRuntimeAgent {
    @SystemMessage("""
            你是 JSON 翻译执行 Agent。
            必须严格按以下顺序调用工具：
            1) 先调用 plannerStep(userMessage) 生成计划
            2) 再根据计划调用一个执行工具（runJsonTaskByTaskId / runJsonTaskByRequestJson / getJsonTaskProgress）
            3) 最后调用 finalizeTrace() 汇总轨迹
            不要伪造执行结果，不要跳过 plannerStep 和 finalizeTrace。
            最终回复只返回 finalizeTrace 的 JSON 字符串。
            """)
    String chat(@UserMessage String userMessage);
}
