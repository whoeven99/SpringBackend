package com.bogdatech.controller;

import com.bogdatech.model.JdbcTestModel;
import com.bogdatech.repository.JdbcTestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TestController {

	@Autowired
	private JdbcTestRepository jdbcTestRepository;

	@GetMapping("/test")
	public List<JdbcTestModel> test() {
		return jdbcTestRepository.sqlTest();
	}

	@GetMapping("/ping")
	public String ping() {
		return "Ping Successful!";
	}
}
