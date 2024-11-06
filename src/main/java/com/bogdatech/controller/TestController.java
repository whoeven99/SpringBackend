package com.bogdatech.controller;


import com.azure.core.credential.AzureKeyCredential;
import com.azure.data.appconfiguration.ConfigurationClient;
import com.azure.data.appconfiguration.ConfigurationClientBuilder;
import com.azure.data.appconfiguration.models.ConfigurationSetting;
import com.bogdatech.integration.ChatGptIntegration;
import com.bogdatech.model.JdbcTestModel;
import com.bogdatech.repository.JdbcTestRepository;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TestController {

	@Autowired
	private JdbcTestRepository jdbcTestRepository;

	@Autowired
	private ChatGptIntegration chatGptIntegration;

	@GetMapping("/test")
	public List<JdbcTestModel> test() {
		return jdbcTestRepository.sqlTest();
	}

	@GetMapping("/ping")
	public String ping() {
		String connectionString = "Endpoint=<your-endpoint>;Id=<your-id>;Secret=<your-secret>";
		String keyName = "<your-key-name>";
		String keyValue = "<your-key-value>";

		ConfigurationClient configurationClient = new ConfigurationClientBuilder()
				.credential(new AzureKeyCredential(keyValue))
				.endpoint(connectionString)
				.buildClient();
		String key = "<your-configuration-key>";

		ConfigurationSetting setting = configurationClient.getConfigurationSetting(key);
		String value = setting.getValue();

		TelemetryClient appInsights = new TelemetryClient();
		appInsights.trackTrace("SpringBackend Ping Successful");
		return "Ping Successful!";
	}

	@GetMapping("/gpt")
	public String chat(@RequestParam String prompt) {
		return chatGptIntegration.chatWithGpt(prompt);
	}
}
