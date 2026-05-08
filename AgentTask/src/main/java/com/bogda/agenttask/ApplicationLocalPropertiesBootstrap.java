package com.bogda.agenttask;

import com.bogda.common.utils.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 与 BogdaTask {@code Configuration} 对齐：把 classpath 下的 {@code application.local.properties}
 *（本地）或 {@code application.properties}（非本地）加载进 {@link ConfigUtils#config}，
 * 以便 {@code ConfigUtils.getConfig("deepseek")} 等键在 AgentTask 中可用。
 * <p>
 * 云上常见仅有其一或均被排除：加载失败<strong>不得</strong>阻止 Spring 启动（嵌套 jar / 类加载器差异下
 * {@link ClassPathResource#exists()} 等也可能异常）。
 */
@Component
public class ApplicationLocalPropertiesBootstrap {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationLocalPropertiesBootstrap.class);

    public ApplicationLocalPropertiesBootstrap() {
        try {
            loadOptionalClasspathProperties();
        } catch (Throwable t) {
            LOG.warn(
                    "Optional classpath application*.properties skipped (ConfigUtils uses env only): {}",
                    t.toString(),
                    t);
        }
    }

    private static void loadOptionalClasspathProperties() throws IOException {
        ClassPathResource resource = resolveClasspathPropertiesFile();
        if (resource == null) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream in = resource.getInputStream()) {
            properties.load(in);
        }
        for (String key : properties.stringPropertyNames()) {
            ConfigUtils.config.put(key, properties.getProperty(key));
        }
        LOG.info("Loaded ConfigUtils entries from classpath resource: {}", safeDescribe(resource));
    }

    private static ClassPathResource resolveClasspathPropertiesFile() {
        boolean local = ConfigUtils.isLocalEnv();
        String primaryName = local ? "application.local.properties" : "application.properties";
        String fallbackName = local ? "application.properties" : "application.local.properties";

        ClassPathResource primary = new ClassPathResource(primaryName);
        if (resourceExists(primary)) {
            return primary;
        }
        ClassPathResource fallback = new ClassPathResource(fallbackName);
        if (resourceExists(fallback)) {
            LOG.warn("Classpath missing {}, loaded {} instead", primaryName, fallbackName);
            return fallback;
        }
        LOG.warn(
                "No application.properties / application.local.properties on classpath (checked {} and {}); "
                        + "ConfigUtils keys rely on environment variables only.",
                primaryName,
                fallbackName);
        return null;
    }

    /** 嵌套 jar 等场景下 exists() 可能抛异常，视为不存在即可 */
    private static boolean resourceExists(ClassPathResource resource) {
        try {
            return resource.exists();
        } catch (RuntimeException ex) {
            LOG.debug("Classpath resource exists check failed: {}", ex.getMessage());
            return false;
        }
    }

    private static String safeDescribe(ClassPathResource resource) {
        try {
            return resource.getDescription();
        } catch (RuntimeException e) {
            return resource.getPath();
        }
    }
}
