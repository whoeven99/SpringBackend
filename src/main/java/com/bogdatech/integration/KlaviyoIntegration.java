package com.bogdatech.integration;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bogdatech.entity.DO.UsersDO;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.bogdatech.constants.KlaviyoConstants.*;

@Component
public class KlaviyoIntegration {

    @Value("${klaviyo.key}")
    private String klaviyoKey;


    // 创建profile
    public String createProfile(UsersDO usersDO) {
        String realKlaviyoKey = "Klaviyo-API-Key " + klaviyoKey;
        String url = BASE_URL + "profiles";

        // 重试次数
        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            // 创建Httpclient对象
            CloseableHttpClient httpClient = HttpClients.createDefault();
            // 创建httpPost请求
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", CONTENT_TYPE_VND_API);
            httpPost.setHeader("accept", ACCEPT);
            httpPost.setHeader("Authorization", realKlaviyoKey);
            httpPost.setHeader("revision", REVISION);
            // 创建查询体
            JSONObject data = new JSONObject();
            data.put("type", "profile");
            JSONObject attributes = new JSONObject();
            attributes.put("email", usersDO.getEmail());
            attributes.put("first_name", usersDO.getShopName());
            data.put("attributes", attributes);
            JSONObject query = new JSONObject();
            query.put("data", data);
            // 设置请求体
            StringEntity input = new StringEntity(query.toString(), "UTF-8");
            httpPost.setEntity(input);
            // 执行请求
            JSONObject jsonObject;
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                // 获取响应实体并转换为JSON格式
                jsonObject = JSONObject.parseObject(EntityUtils.toString(response.getEntity(), "UTF-8"));
                // 获取"data"对象
                data = jsonObject.getJSONObject("data");

                // 从"data"对象中获取"id"字段
                if (data != null && data.containsKey("id")) {
                    return data.getString("id");
                } else {
                    JSONArray errors = jsonObject.getJSONArray("errors");
                    if (errors != null ) {
                        return errors.getJSONObject(0).getObject("meta", JSONObject.class).getString("duplicate_profile_id");
                    }
                }
                response.close();
                httpClient.close();
            } catch (IOException e) {
                // 重试机制
                retryCount++;
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(1000); // 等待一段时间再重试，避免频繁请求
                    } catch (InterruptedException ie) {
                        //中断当前线程
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }
        return null;
    }


    // 将profile加入到List集合里面
    public Boolean addProfileToKlaviyoList(String profileId, String listId) {
        String realKlaviyoKey = "Klaviyo-API-Key " + klaviyoKey;
        String url = BASE_URL + "lists/"+ listId + "/relationships/profiles";

        // 重试次数
        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            // 创建Httpclient对象
            CloseableHttpClient httpClient = HttpClients.createDefault();
            // 创建httpPost请求
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", CONTENT_TYPE_VND_API);
            httpPost.setHeader("accept", ACCEPT);
            httpPost.setHeader("Authorization", realKlaviyoKey);
            httpPost.setHeader("revision", REVISION);
            // 创建查询体
            JSONArray data = new JSONArray();
            Map<String, String> profile = new HashMap<>();
            profile.put("type", "profile");
            profile.put("id", profileId);
            data.add(0, profile);
            JSONObject query = new JSONObject();
            query.put("data", data);
            // 设置请求体
            StringEntity input = new StringEntity(query.toString(), "UTF-8");
            httpPost.setEntity(input);
//            appInsights.trackTrace("query: " + query);
            // 执行请求
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                // 获取响应实体并转换为JSON格式
                if (response.getEntity() == null){
                    return true;
                }
//                appInsights.trackTrace("jsonObject: " + EntityUtils.toString(response.getEntity()));
                // 获取"data"对象
                response.close();
                httpClient.close();
                return false;
            } catch (IOException e) {
                // 重试机制
                retryCount++;
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(1000); // 等待一段时间再重试，避免频繁请求
                    } catch (InterruptedException ie) {
                        //中断当前线程
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }
        return false;
    }

}



