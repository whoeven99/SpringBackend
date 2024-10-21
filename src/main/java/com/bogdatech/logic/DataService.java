package com.bogdatech.logic;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DataService {
    private final Map<String, LinkedHashMap<String, Object>> value = new ConcurrentHashMap<>();

    public void updateValue(String key, LinkedHashMap<String, Object> data) {
        synchronized (value) {
            value.clear(); // 清空旧数据
            value.put(key,data); // 存储新数据
        }
    }

    public Map<String, Object> getData() {
        // 返回不可修改的Map视图以防止外部修改
        return Collections.unmodifiableMap(value);
    }
}
