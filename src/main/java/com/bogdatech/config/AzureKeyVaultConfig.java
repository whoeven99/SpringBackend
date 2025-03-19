package com.bogdatech.config;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureKeyVaultConfig {
    @Bean
    public SecretClient secretClient() {
        // 使用 DefaultAzureCredential 自动检测身份
        DefaultAzureCredential defaultAzureCredential = new DefaultAzureCredentialBuilder().build();
        return new SecretClientBuilder()
                .vaultUrl("https://springbackendvault.vault.azure.net/")
                .credential(defaultAzureCredential)
                .buildClient();
    }

}
