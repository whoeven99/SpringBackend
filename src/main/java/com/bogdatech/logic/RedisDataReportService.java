package com.bogdatech.logic;

import com.bogdatech.entity.VO.UserDataReportVO;
import com.bogdatech.integration.RedisIntegration;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.RedisKeyUtils.*;
import static com.mysql.cj.util.TimeUtil.DATE_FORMATTER;

@Service
public class RedisDataReportService {
    @Autowired
    private RedisIntegration redisIntegration;

    /**
     * 以set的形式存储用户上报的数据
     * dr:{shopName}:{language}:{eventName}
     * */
    public void saveUserDataReport(String shopName, UserDataReportVO userDataReportVO) {
        //对时间进行处理，最小单位为天
        LocalDate date = userDataReportVO.getTimestamp().toLocalDateTime().toLocalDate(); // 只取日期部分
        String format = date.format(DATE_FORMATTER);
        String dataReportKey = generateDataReportKey(shopName, userDataReportVO.getStoreLanguage()[0], format);
        //对传入的client_id做去重，然后再加一
        String clientIdSetKey = generateClientIdSetKey(shopName, userDataReportVO.getStoreLanguage()[0], format, userDataReportVO.getEventName());
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
     * */
    public String getUserDataReport(String shopName, String[] languages, Timestamp timestamp, int dayData) {
        Map<String, Object> allMap = new HashMap<>();
        LocalDate baseDate  = timestamp.toLocalDateTime().toLocalDate(); // 只取日期部分
        for (String languageCode: languages
        ) {
            Map<String, Object> languageMap = new HashMap<>();
            for (int i = 0; i < dayData; i++) {
                LocalDate date = baseDate.minusDays(i);
                String format = date.format(DATE_FORMATTER);
                //对时间进行处理，最小单位为天
                //date减去对应的一天
                String dataReportKey = generateDataReportKey(shopName, languageCode, format);
                Map<Object, Object> hashAll = redisIntegration.getHashAll(dataReportKey);
                languageMap.put(format, hashAll);
            }
            allMap.put(languageCode, languageMap);
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(allMap);
        } catch (JsonProcessingException e) {
            appInsights.trackException(e);
            appInsights.trackTrace("getUserDataReport 用户： " + shopName + " target: " + Arrays.toString(languages));
            return null;
        }
    }
}
