package com.bogdatech.integration;

import com.bogdatech.model.controller.request.MailChampSendEmailRequest;
import com.bogdatech.model.controller.request.TencentSendEmailRequest;
import com.bogdatech.requestBody.EmailRequestBody;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.applicationinsights.TelemetryClient;
import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.ses.v20201002.SesClient;
import com.tencentcloudapi.ses.v20201002.models.SendEmailRequest;
import com.tencentcloudapi.ses.v20201002.models.SendEmailResponse;
import com.tencentcloudapi.ses.v20201002.models.Template;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class EmailIntegration {

    @Value("${email.key}")
    private String mailChimpKey;

    TelemetryClient appInsights = new TelemetryClient();
    public  String sendEmail(MailChampSendEmailRequest sendEmailRequest) {
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

    //腾讯邮件发送
    public Boolean sendEmailByTencent(TencentSendEmailRequest tencentSendEmailRequest) {
        Map<String, String> templateData = tencentSendEmailRequest.getTemplateData();
        ObjectMapper objectMapper = new ObjectMapper();

        String jsonString = null;
        try {
            Credential cred = new Credential(System.getenv("Tencent_Cloud_KEY_ID"), System.getenv("Tencent_Cloud_KEY"));
            String templateDataJson = objectMapper.writeValueAsString(templateData);
            // 实例化一个http选项，可选的，没有特殊需求可以跳过
            HttpProfile httpProfile = new HttpProfile();
//            httpProfile.setEndpoint("ses.ap-guangzhou-open.tencentcloudapi.com");
            // 实例化一个client选项，可选的，没有特殊需求可以跳过
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            // 实例化要请求产品的client对象,clientProfile是可选的
            SesClient client = new SesClient(cred, "ap-hongkong", clientProfile);
            // 实例化一个请求对象,每个接口都会对应一个request对象
            SendEmailRequest req = new SendEmailRequest();
            req.setFromEmailAddress(tencentSendEmailRequest.getFromEmail());

            String[] destination1 = {tencentSendEmailRequest.getToEmail()};
            req.setDestination(destination1);

            req.setSubject(tencentSendEmailRequest.getSubject());
            Template template1 = new Template();
            template1.setTemplateID(tencentSendEmailRequest.getTemplateId());
            template1.setTemplateData(templateDataJson);
            req.setTemplate(template1);

            // 返回的resp是一个SendEmailResponse的实例，与请求对象对应
            SendEmailResponse resp = client.SendEmail(req);
            // 输出json格式的字符串回包
            jsonString = AbstractModel.toJsonString(resp);
//            System.out.println("jsonString: " + jsonString);
        } catch (TencentCloudSDKException e) {
            appInsights.trackTrace(e.toString());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        //判断服务的返回值是否含有RequestId

        assert jsonString != null;
        return jsonString.contains("RequestId");
        }
    }
