package com.bogdatech.config;

import com.bogdatech.Application;
import com.bogdatech.utils.ConfigUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Properties;

@Component
public class Configuration {
    // 有别的更好的初始化方法，后续更改
    public Configuration() throws IOException {
        Properties properties = new Properties();
        String env = System.getenv("env");
        if (env != null) {
            properties.load(Application.class.getClassLoader().getResourceAsStream("application.properties"));
        } else {
            properties.load(Application.class.getClassLoader().getResourceAsStream("application.local.properties"));
        }

        // 将 properties 的内容加载到 config 中
        for (String key : properties.stringPropertyNames()) {
            ConfigUtils.config.put(key, properties.getProperty(key));
        }
    }
}