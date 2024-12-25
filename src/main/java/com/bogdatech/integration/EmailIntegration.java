package com.bogdatech.integration;

import com.bogdatech.model.controller.request.SendEmailRequest;
import com.bogdatech.requestBody.EmailRequestBody;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class EmailIntegration {

    @Value("${email.key}")
    private String mailChimpKey;

    public  String sendEmail(SendEmailRequest sendEmailRequest) {
        sendEmailRequest.setEmailKey(mailChimpKey);
//        System.out.println("mailChimpKey: " + mailChimpKey);
        EmailRequestBody emailRequestBody = new EmailRequestBody();
        String sendEmail = emailRequestBody.sendEmail(sendEmailRequest);
//        System.out.println("sendEmail: " + sendEmail);
        String url = "https://mandrillapp.com/api/1.0/messages/send-template";
        // 重试次数
        int maxRetries = 3;
        int retryCount = 0;
        while (retryCount < maxRetries) {
            // 创建Httpclient对象
            CloseableHttpClient httpClient = HttpClients.createDefault();
            // 创建httpPost请求
            HttpPost httpPost = new HttpPost(url);
            httpPost.addHeader("Content-Type", "application/json");
            // 设置请求体
            StringEntity input = new StringEntity(sendEmail, "UTF-8");
            httpPost.setEntity(input);
            // 执行请求
//            JSONObject jsonObject;
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                // 获取响应实体并转换为JSON格式
                String entity = EntityUtils.toString(response.getEntity(), "UTF-8");
//                System.out.println("jsonObject: " + jsonObject.toString());
                // 获取status对象
                if (entity.contains("sent")) {
                    return "Success";
                }

                response.close();
                httpClient.close();
                return "Error: " + entity;
            } catch (IOException e) {
                // 重试机制
                retryCount++;
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(1000); // 等待一段时间再重试，避免频繁请求
                    } catch (InterruptedException ie) {
                        //中断当前线程
                        Thread.currentThread().interrupt();
                        return "Error: " + ie.getMessage();
                    }
                } else {
                    return "Error: " + e.getMessage();
                }
            }
        }
        return "Error: Max retries exceeded";
    }


}
