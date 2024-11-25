package com.bogdatech.controller;


import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.integration.ChatGptIntegration;
import com.bogdatech.Service.impl.TranslatesServiceImpl;
import com.bogdatech.model.JdbcTestModel;
import com.bogdatech.repository.JdbcTestRepository;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class TestController {

	private JdbcTestRepository jdbcTestRepository;
	@Autowired
	private TranslatesServiceImpl translatesServiceImpl;

	@Autowired
	private ChatGptIntegration chatGptIntegration;

	@GetMapping("/test")
	public List<JdbcTestModel> test() {
		return jdbcTestRepository.sqlTest();
	}

	@GetMapping("/ping")
	public String ping() {
		TelemetryClient appInsights = new TelemetryClient();
		appInsights.trackTrace("SpringBackend Ping Successful");
		return "Ping Successful!";
	}

	@GetMapping("/gpt")
	public String chat(@RequestParam String prompt) {
		return chatGptIntegration.chatWithGpt(prompt);
	}

	@PostMapping("/test/test1")
	public int test1(@RequestBody TranslatesDO name) {
		return translatesServiceImpl.updateTranslateStatus(name.getShopName(),name.getStatus(),name.getTarget());
	}
}
