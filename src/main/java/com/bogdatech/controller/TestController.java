package com.bogdatech.controller;

import com.bogdatech.integration.ChatGptIntegration;
import com.bogdatech.model.JdbcTestModel;
import com.bogdatech.repository.JdbcTestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
		return "Ping Successful!";
	}

	@GetMapping("/gpt")
	public String chat(@RequestParam String prompt) {
		return chatGptIntegration.chatWithGpt(prompt);
	}
}
