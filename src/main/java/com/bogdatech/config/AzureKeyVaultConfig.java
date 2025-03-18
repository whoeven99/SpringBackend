package com.bogdatech.config;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureKeyVaultConfig {
    @Bean
    public SecretClient secretClient() {
        // 使用 Client ID & Secret 认证
        ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                .clientId("bbe52adc-91cb-4de7-bb26-f005ee9cd2cb")
                .clientSecret("817e6f37d682457f8bf85016b30ae3ce")
                .tenantId("7e4cb437-91a6-4a28-88e5-6549623374c5")
                .build();

        return new SecretClientBuilder()
                .vaultUrl("https://SpringBackendVault.vault.azure.net/")
                .credential(credential)
                .buildClient();
    }

}
