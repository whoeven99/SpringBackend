package com.bogda.service.config;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.bogda.common.utils.ConfigUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureKeyVaultConfig {
    @Bean
    public SecretClient secretClient() {
        // 根据环境变量获取clientId和keyVaultUrl来判断选择什么
        if (!ConfigUtils.isLocalEnv()) {
            String clientId = ConfigUtils.getConfig("Client_ID");
            String keyVaultUrl = ConfigUtils.getConfig("UserPrivateKeyVaultUrl");
            ManagedIdentityCredential credential = new ManagedIdentityCredentialBuilder()
                    .clientId(clientId)
                    .build();

            return new SecretClientBuilder()
                    .vaultUrl(keyVaultUrl)
                    .credential(credential)
                    .buildClient();
        } else {
            //使用 DefaultAzureCredential 自动检测身份
            DefaultAzureCredential defaultAzureCredential = new DefaultAzureCredentialBuilder().build();
            return new SecretClientBuilder()
                    .vaultUrl("https://springbackendvault.vault.azure.net/")
                    .credential(defaultAzureCredential)
                    .buildClient();
        }
    }
}
