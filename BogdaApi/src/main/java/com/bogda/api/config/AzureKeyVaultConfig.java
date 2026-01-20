package com.bogda.api.config;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.bogda.common.utils.ConfigUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureKeyVaultConfig {
    @Value("${key.vault.url}")
    private String url;

    @Bean
    public SecretClient secretClient() {
        TokenCredential credential;
        if (!ConfigUtils.isLocalEnv()) {
            // 生产环境使用managed identity
            credential = new ManagedIdentityCredentialBuilder()
                    .clientId(ConfigUtils.getConfig("Client_ID")).build();
        } else {
            // 本地的开发环境使用默认凭据
            credential = new DefaultAzureCredentialBuilder().build();
        }
        return new SecretClientBuilder()
                .vaultUrl(url)
                .credential(credential)
                .buildClient();
    }
}
