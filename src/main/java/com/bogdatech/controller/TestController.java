package com.bogdatech.controller;

import com.bogdatech.integration.ChatGptIntegration;
import com.bogdatech.logic.BasicRateService;
import com.bogdatech.logic.DataService;
import com.bogdatech.model.JdbcTestModel;
import com.bogdatech.model.controller.request.BasicRateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.JdbcTestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.bogdatech.common.enums.BasicEnum.TRUE;

@RestController
public class TestController {

	@Autowired
	private JdbcTestRepository jdbcTestRepository;
	@Autowired
	private ChatGptIntegration chatGptIntegration;
	@Autowired
	private BasicRateService basicRateService;
	@Autowired
	private DataService dataService;

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

	@PostMapping("/getRate")
	public BaseResponse getRate(@RequestBody BasicRateRequest basicRateRequest) throws Exception {
		return basicRateService.getBasicRate(basicRateRequest);
	}

	@PostMapping("/getRateValue")
	public BaseResponse getRateValue(){
		return new BaseResponse(TRUE.getSuccess(), TRUE.getErrorMessage(), dataService.getData());
	}
}
