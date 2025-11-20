package com.bogdatech.config;

import com.bogdatech.utils.ConfigUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Component
public class Configuration {
    // 有别的更好的初始化方法，后续更改
    public Configuration() throws IOException {
        Properties properties = new Properties();
        String env = System.getenv("ApplicationEnv");
        String fileName = (env != null)
                ? "application.properties"
                : "application.local.properties";

        ClassPathResource resource = new ClassPathResource(fileName);

        try (InputStream in = resource.getInputStream()) {
            properties.load(in);
        }

        // 加载到 ConfigUtils
        for (String key : properties.stringPropertyNames()) {
            ConfigUtils.config.put(key, properties.getProperty(key));
        }
    }
}