package com.bogda.api.integration;

import com.bogda.api.model.controller.request.CloudServiceRequest;
import com.bogda.api.utils.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TestingEnvironmentIntegration {
    @Autowired
    private BaseHttpIntegration baseHttpIntegration;

    public String sendApiByTestCloud(String api, String shopName, String accessToken, String apiVersion, String query) {
        CloudServiceRequest cloudServiceRequest = new CloudServiceRequest();
        cloudServiceRequest.setShopName(shopName);
        cloudServiceRequest.setAccessToken(accessToken);
        cloudServiceRequest.setTarget(apiVersion);
        cloudServiceRequest.setBody(query);

        String body = JsonUtils.objectToJson(cloudServiceRequest);
        return baseHttpIntegration.httpPost(
                "https://springbackendservice-e3hgbjgqafb9cpdh.canadacentral-01.azurewebsites.net/" + api,
                body);
    }
}
