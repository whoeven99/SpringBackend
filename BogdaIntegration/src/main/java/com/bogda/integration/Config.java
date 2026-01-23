package com.bogda.integration;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.google.genai.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Config {
    @Value("${keyvault.endpoint}")
    private String url;

    @Value("${appRegistration.client-id}")
    private String clientId;

    @Value("${Azure_Client_Secret}")
    private String clientSecret;

    @Value("${appRegistration.tenant-id}")
    private String tenantId;

    @Bean
    public SecretClient secretClient() {
        ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .tenantId(tenantId)
                .build();

        return new SecretClientBuilder()
                .vaultUrl(url)
                .credential(credential)
                .buildClient();
    }

    @Value("${gemini.api.key.vault}")
    private String key;

    @Bean
    public Client geminiClient() {
        return Client.builder()
                .apiKey(key)
                .build();
    }
}
