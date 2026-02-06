package com.bogda.integration.aimodel;

import com.aliyun.openservices.log.Client;
import com.aliyun.openservices.log.common.LogItem;
import com.aliyun.openservices.log.common.LogContent;
import com.aliyun.openservices.log.common.QueriedLog;
import com.aliyun.openservices.log.exception.LogException;
import com.aliyun.openservices.log.request.PutLogsRequest;
import com.aliyun.openservices.log.request.GetLogsRequest;
import com.aliyun.openservices.log.response.GetLogsResponse;
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
        if (accessKeyId == null || accessKeySecret == null ||
                endpoint == null) {
            return;
        }

        client = new Client(endpoint, accessKeyId, accessKeySecret);
    }

    /** 替代 trackTrace：写一条普通业务 Trace 日志 */
    public void logTrace(String traceName, String message) {
        LogItem item = new LogItem((int) (System.currentTimeMillis() / 1000));
        item.PushBack("type", "trace");
        item.PushBack("traceName", traceName);
        item.PushBack("message", message);

        List<LogItem> items = new ArrayList<>();
        items.add(item);

        PutLogsRequest request = new PutLogsRequest(project, logstore, "", "", items);
        try {
            client.PutLogs(request);
        } catch (LogException e) {
            // 写 SLS 失败时，你可以打本地日志，不要再死循环写 SLS
            e.printStackTrace();
        }
    }

    /** 替代 trackException：记录异常信息 + 堆栈 */
    public void logException(String scene, Throwable ex) {
        LogItem item = new LogItem((int) (System.currentTimeMillis() / 1000));
        item.PushBack("type", "exception");
        item.PushBack("scene", scene);
        item.PushBack("exceptionType", ex.getClass().getName());
        item.PushBack("message", ex.getMessage());

        // 简单拼一个堆栈字符串（线上可考虑截断）
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : ex.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        item.PushBack("stackTrace", sb.toString());

        List<LogItem> items = new ArrayList<>();
        items.add(item);

        PutLogsRequest request = new PutLogsRequest(project, logstore, "", "", items);
        try {
            client.PutLogs(request);
        } catch (LogException e) {
            e.printStackTrace();
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
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }
}
