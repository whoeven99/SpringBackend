package com.bogda.common.agent;

/**
 * JSON Runtime Agent 执行入口；实现类由 AgentTask 模块提供。
 */
public interface JsonRuntimeAgentRunner {

    /**
     * @param message 自然语言或带 taskId / 完整 JSON 的文本
     * @return finalizeTrace 产出的 JSON 字符串
     */
    String run(String message);
}
