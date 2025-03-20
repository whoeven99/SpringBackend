package com.bogdatech.config;

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
        String clientId = "7890ba67-d8e5-4aa1-a67c-9bb4a9a93b91"; //  clientId
        String keyVaultUrl = "https://springbackendvault.vault.azure.net/"; //  keyVaultUrl
        ManagedIdentityCredential credential = new ManagedIdentityCredentialBuilder()
                .clientId(clientId)
                .build();

        return new SecretClientBuilder()
                .vaultUrl(keyVaultUrl)
                .credential(credential)
                .buildClient();
        // 使用 DefaultAzureCredential 自动检测身份
//        DefaultAzureCredential defaultAzureCredential = new DefaultAzureCredentialBuilder().build();
//        return new SecretClientBuilder()
//                .vaultUrl("https://springbackendvault.vault.azure.net/")
//                .credential(defaultAzureCredential)
//                .buildClient();
    }

}
