package com.bogdatech.config;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureKeyVaultConfig {
    @Bean
    public SecretClient secretClient() {
        // 根据环境变量获取clientId和keyVaultUrl来判断选择什么
        String env = System.getenv("ApplicationEnv");
        if ("prod".equals(env) || "dev".equals(env)) {
            String clientId = System.getenv("Client_ID");
            String keyVaultUrl = System.getenv("UserPrivateKeyVaultUrl");
            // 如果是本地开发环境，则用DefaultAzureCredential自动检验身份
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
