package com.bogda.integration;

import com.azure.identity.EnvironmentCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.google.genai.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Key Vault：使用 {@link EnvironmentCredentialBuilder}，按 Azure 约定从<strong>环境变量</strong>读取凭据，
 * 不再使用 {@code appRegistration.client-id} / {@code Azure_Client_Secret} 等 Spring 属性。
 * <p>
 * 必填环境变量（与服务主体访问 Vault 一致）：{@code AZURE_CLIENT_ID}、{@code AZURE_TENANT_ID}、{@code AZURE_CLIENT_SECRET}。
 * 可选：若仅用托管身份等，也可依赖 {@code DefaultAzureCredential} 链路（此处采用环境变量方式，便于 Render 等非 Azure 托管环境）。
 */
@Configuration
public class Config {

    @Value("${keyvault.endpoint}")
    private String vaultUrl;

    @Bean
    public SecretClient secretClient() {
        var credential = new EnvironmentCredentialBuilder().build();
        return new SecretClientBuilder()
                .vaultUrl(vaultUrl)
                .credential(credential)
                .buildClient();
    }

    /**
     * 优先 {@code gemini.api.key}，兼容旧属性 {@code gemini.api.key.vault}；还可使用环境变量 {@code GEMINI_API_KEY}。
     */
    @Value("${gemini.api.key:${gemini.api.key.vault:}}")
    private String geminiApiKeyFromProperties;

    @Bean
    public Client geminiClient() {
        String key = firstNonBlank(
                geminiApiKeyFromProperties,
                System.getenv("GEMINI_API_KEY")
        );
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "Configure Gemini API key: property gemini.api.key (or legacy gemini.api.key.vault), or env GEMINI_API_KEY");
        }
        return Client.builder()
                .apiKey(key.trim())
                .build();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return "";
    }
}
