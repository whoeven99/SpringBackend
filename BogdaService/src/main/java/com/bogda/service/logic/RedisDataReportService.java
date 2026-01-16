package com.bogda.service.logic;

import com.bogda.service.entity.VO.UserDataReportVO;
import com.bogda.service.integration.RedisIntegration;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import static com.bogda.common.utils.RedisKeyUtils.*;
import static com.mysql.cj.util.TimeUtil.DATE_FORMATTER;

@Service
public class RedisDataReportService {
    @Autowired
    private RedisIntegration redisIntegration;

    /**
     * 以set的形式存储用户上报的数据
     * dr:{shopName}:{language}:{eventName}
     */
    public void saveUserDataReport(String shopName, UserDataReportVO userDataReportVO) {
        //对时间进行处理，最小单位为天
        LocalDate date = userDataReportVO.getTimestamp().toLocalDateTime().toLocalDate(); // 只取日期部分
        String format = date.format(DATE_FORMATTER);
        String dataReportKey = DATA_REPORT_KEY_TEMPLATE.replace("{shopName}", shopName)
                .replace("{language}", userDataReportVO.getStoreLanguage()[0])
                .replace("{yyyyMMdd}", format);

        //对传入的client_id做去重，然后再加一
        String clientIdSetKey = CLIENT_ID_SET.replace("{shopName}", shopName)
                .replace("{language}", userDataReportVO.getStoreLanguage()[0])
                .replace("{yyyyMMdd}", format)
                .replace("{eventName}", userDataReportVO.getEventName());
        String setKeys = DATA_REPORT_KEY_TEMPLATE_KEYS.replace("{shopName}", shopName);
        redisIntegration.setSet(setKeys, userDataReportVO.getStoreLanguage()[0]);
        Boolean flag = redisIntegration.setSet(clientIdSetKey, userDataReportVO.getClientId());
        if (flag) {
            redisIntegration.expire(clientIdSetKey, DAY_1);
            Long count = redisIntegration.incrementHash(dataReportKey, userDataReportVO.getEventName(), 1L);
            if (count != null && count == 1) {
                redisIntegration.expire(dataReportKey, DAY_15);
            }
        }
    }

    /**
     * 读取用户上报的数据
     */
    public String getUserDataReport(String shopName, Timestamp timestamp, int dayData) {
        // 最终返回的数据结构： { languageCode -> { date -> {hash数据} } }
        Map<String, Map<String, Map<String, String>>> allMap = new HashMap<>();
        LocalDate baseDate = timestamp.toLocalDateTime().toLocalDate();

        String setKeys = DATA_REPORT_KEY_TEMPLATE_KEYS.replace("{shopName}", shopName);
        Set<String> redisIntegrationSet = redisIntegration.getSet(setKeys);

        if (redisIntegrationSet == null || redisIntegrationSet.isEmpty()) {
            AppInsightsUtils.trackTrace("getUserDataReport 没有找到 key, shopName=" + shopName);
            return null;
        }

        for (String languageCode : redisIntegrationSet) {
            // 每个语言对应一个独立的 languageMap
            Map<String, Map<String, String>> languageMap = new HashMap<>();

            // 遍历 dayData 天的数据
            for (int i = 0; i < dayData; i++) {
                LocalDate date = baseDate.minusDays(i);
                String format = date.format(DATE_FORMATTER);

                String key = DATA_REPORT_KEY_TEMPLATE.replace("{shopName}", shopName)
                        .replace("{language}", languageCode)
                        .replace("{yyyyMMdd}", format);
                Map<String, String> hashAll = redisIntegration.hGetAll(key);

                if (hashAll != null && !hashAll.isEmpty()) {
                    languageMap.put(format, hashAll);
                }
            }
            allMap.put(languageCode, languageMap);
        }

        try {
            return JsonUtils.OBJECT_MAPPER.writeValueAsString(allMap);
        } catch (JsonProcessingException e) {
            AppInsightsUtils.trackException(e);
            AppInsightsUtils.trackTrace("getUserDataReport JSON序列化失败, shopName=" + shopName);
            return null;
        }
    }
}
