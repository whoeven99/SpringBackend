package com.bogda.agenttask;

import com.bogda.common.utils.ConfigUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 与 BogdaTask {@code Configuration} 对齐：把 classpath 下的 {@code application.local.properties}
 *（本地）或 {@code application.properties}（非本地）加载进 {@link ConfigUtils#config}，
 * 以便 {@code ConfigUtils.getConfig("deepseek")} 等键在 AgentTask 中可用。
 */
@Component
public class ApplicationLocalPropertiesBootstrap {

    public ApplicationLocalPropertiesBootstrap() throws IOException {
        Properties properties = new Properties();
        String fileName = "application" + (ConfigUtils.isLocalEnv() ? ".local" : "") + ".properties";
        ClassPathResource resource = new ClassPathResource(fileName);
        try (InputStream in = resource.getInputStream()) {
            properties.load(in);
        }
        for (String key : properties.stringPropertyNames()) {
            ConfigUtils.config.put(key, properties.getProperty(key));
        }
    }
}
