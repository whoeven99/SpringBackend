package com.bogda.integration.aimodel;

import com.aliyun.openservices.log.Client;
import com.aliyun.openservices.log.common.LogItem;
import com.aliyun.openservices.log.common.LogContent;
import com.aliyun.openservices.log.common.QueriedLog;
import com.aliyun.openservices.log.request.PutLogsRequest;
import com.aliyun.openservices.log.request.GetLogsRequest;
import com.aliyun.openservices.log.response.GetLogsResponse;
import com.bogda.common.utils.AppInsightsUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AliyunSlsIntegration {
    @Value("${ali.sls.access.key.vault}")
    private String accessKeyId;
    @Value("${ali.sls.access.secret.key.vault}")
    private String accessKeySecret;
    @Value("${ali.sls.endpoint.key.vault}")
    private String endpoint;
    @Value("${ali.sls.project.key.vault}")
    private String project;
    @Value("${ali.sls.logstore.key.vault}")
    private String logstore;
    private Client client;

    /**
     * 初始化SLS客户端连接
     */
    @PostConstruct
    public void initClient() {
        try {
            System.out.println("accessKeyId: " + accessKeyId + " accessKeySecret: " + accessKeySecret + " endpoint: " + endpoint + " project: " + project + " logstore: " + logstore);
            if (accessKeyId == null || accessKeySecret == null ||
                    endpoint == null) {
                AppInsightsUtils.trackTrace("FatalException AliyunSlsIntegration 配置缺失，无法初始化SLS客户端");
                return;
            }

            client = new Client(endpoint, accessKeyId, accessKeySecret);
            AppInsightsUtils.trackTrace("AliyunSlsIntegration 客户端初始化成功");
        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException AliyunSlsIntegration 初始化失败: " + e.getMessage());
        }
    }

    /**
     * 写入日志数据到SLS
     *
     * @param topic  日志主题
     * @param source 日志来源
     * @param logs   日志内容，key为字段名，value为字段值
     * @return 是否写入成功
     */
    public boolean writeLogs(String topic, String source, Map<String, String> logs) {
        if (client == null) {
            AppInsightsUtils.trackTrace("FatalException AliyunSlsIntegration 客户端未初始化");
            return false;
        }

        if (project == null || logstore == null) {
            AppInsightsUtils.trackTrace("FatalException AliyunSlsIntegration PROJECT或LOGSTORE未配置");
            return false;
        }

        try {
            List<LogItem> logItems = new ArrayList<>();
            LogItem logItem = new LogItem();

            // 设置时间戳
            logItem.SetTime((int) (System.currentTimeMillis() / 1000));

            // 添加日志内容
            for (Map.Entry<String, String> entry : logs.entrySet()) {
                logItem.PushBack(entry.getKey(), entry.getValue());
            }

            logItems.add(logItem);

            // 写入日志
            PutLogsRequest request = new PutLogsRequest(project, logstore, topic, source, logItems);
            client.PutLogs(request);

            AppInsightsUtils.trackTrace("AliyunSlsIntegration 写入日志成功，topic: " + topic);
            return true;
        } catch (Exception e) {
            AppInsightsUtils.trackException(e);
            AppInsightsUtils.trackTrace("FatalException AliyunSlsIntegration 写入日志异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 读取日志数据
     *
     * @param from  开始时间（Unix时间戳，秒）
     * @param to    结束时间（Unix时间戳，秒）
     * @param query 查询语句，例如："* | select *"
     * @return 日志数据列表
     */
    public List<Map<String, String>> readLogs(int from, int to, String query) {
        List<Map<String, String>> result = new ArrayList<>();

        try {
            GetLogsRequest request = new GetLogsRequest(project, logstore, from, to, "", query);

            GetLogsResponse response = client.GetLogs(request);

            for (QueriedLog log : response.getLogs()) {
                Map<String, String> row = new HashMap<>();
                for (LogContent content : log.GetLogItem().mContents) {
                    row.put(content.getKey(), content.getValue());
                }
                result.add(row);
            }

            System.out.println("AliyunSlsIntegration 聚合查询成功，数量: " + result.size());
        } catch (Exception e) {
            System.out.println("AliyunSlsIntegration 聚合查询失败: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * 检查客户端连接状态
     *
     * @return 是否已连接
     */
    public boolean isConnected() {
        return client != null;
    }
}
