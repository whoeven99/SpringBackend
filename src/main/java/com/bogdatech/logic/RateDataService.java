package com.bogdatech.logic;

import com.bogdatech.integration.RateHttpIntegration;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateDataService {

    private final Map<String, LinkedHashMap<String, Object>> value = new ConcurrentHashMap<>();

    public void updateValue(String key, LinkedHashMap<String, Object> data) {
        synchronized (value) {
            value.clear(); // 清空旧数据
            value.put(key, data); // 存储新数据
        }
    }

    public Map<String, Object> getData() {
        // 返回不可修改的Map视图以防止外部修改
        return Collections.unmodifiableMap(value);
    }

    //前端传入两个货币代码，返回他们对应的汇率。获取rateMap数据，因为是以欧元为基础，所以要做处理
    public double getRateByRateMap(String from, String to) {
        Double fromRate = RateHttpIntegration.rateMap.get(from);
        Double toRate = RateHttpIntegration.rateMap.get(to);
        Double result = toRate / fromRate;
        return result;
    }
}
