package com.bogda.api;

import com.bogda.api.integration.BaseHttpIntegration;
import com.bogda.common.utils.JsonUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class BaseHttpIntegrationTest {

    @Autowired
    private BaseHttpIntegration baseHttpIntegration;
    
    @MockBean
    private CloseableHttpClient httpClient;

    @BeforeEach
    public void setUp() {
        // Mock the execute method of httpClient
        CloseableHttpResponse mockResponse = Mockito.mock(CloseableHttpResponse.class);
        try {
            HttpEntity entity = Mockito.mock(HttpEntity.class);
            Mockito.when(entity.getContent()).thenReturn(new ByteArrayInputStream("{\"status\":\"success\"}".getBytes()));
            Mockito.when(mockResponse.getEntity()).thenReturn(entity);
            Mockito.when(httpClient.execute(Mockito.any(HttpPost.class))).thenReturn(mockResponse);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testSendHttpPost() throws Exception {
        // Mock URL, request body, and headers
        String url = "testurl";
        Map<String, String> map = new HashMap<>();
        map.put("shopName", "ciwishop.myshopify.com");
        map.put("addChars", "10");

        String ans = baseHttpIntegration.httpPost(url, JsonUtils.objectToJson(map), new HashMap<>());

        assertEquals("{\"status\":\"success\"}", ans);
    }
}
