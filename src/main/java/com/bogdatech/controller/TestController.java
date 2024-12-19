package com.bogdatech.controller;


import com.alibaba.fastjson.JSONObject;
import com.bogdatech.Service.impl.TranslatesServiceImpl;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.integration.ChatGptIntegration;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class TestController {

//	private JdbcTestRepository jdbcTestRepository;
	@Autowired
	private TranslatesServiceImpl translatesServiceImpl;

	@Autowired
	private ChatGptIntegration chatGptIntegration;

	@Autowired
	private ShopifyHttpIntegration shopifyApiIntegration;
//	@GetMapping("/test")
//	public List<JdbcTestModel> test() {
//		return jdbcTestRepository.sqlTest();
//	}

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
		return translatesServiceImpl.updateTranslateStatus(name.getShopName(),name.getStatus(),name.getTarget(), name.getSource(), name.getAccessToken());
	}

	//通过测试环境调shopify的API
	@PostMapping("/test123")
	public String test(@RequestBody CloudServiceRequest cloudServiceRequest) {
		ShopifyRequest request = new ShopifyRequest();
		request.setShopName(cloudServiceRequest.getShopName());
		request.setAccessToken(cloudServiceRequest.getAccessToken());
		request.setTarget(cloudServiceRequest.getTarget());
		String body = cloudServiceRequest.getBody();
		JSONObject infoByShopify = shopifyApiIntegration.getInfoByShopify(request, body);
		return infoByShopify.toString();
	}
}
