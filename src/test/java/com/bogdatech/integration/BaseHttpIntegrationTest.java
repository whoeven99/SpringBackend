package com.bogdatech.integration;

import com.bogdatech.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class BaseHttpIntegrationTest {

    @Autowired
    private BaseHttpIntegration baseHttpIntegration;

    @Test
    public void testSendHttpPost() throws Exception {
        // Mock URL, request body, and headers
        String url = "https://springbackendservice-e3hgbjgqafb9cpdh.canadacentral-01.azurewebsites.net/todoBConfig";
        Map<String, String> map = new HashMap<>();
        map.put("shopName", "ciwishop.myshopify.com");
        map.put("addChars", "10");

        String ans = baseHttpIntegration.httpPost(url, JsonUtils.objectToJson(map), new HashMap<>());

        var ss = 1;
    }
}
