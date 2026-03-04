package com.bogda.service.logic;

import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.bogda.repository.entity.UserPrivateTranslateDO;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.repository.repo.UserPrivateTranslateRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class UserPrivateTranslateService {
    @Autowired
    private UserPrivateTranslateRepo userPrivateTranslateRepo;
    @Autowired
    private SecretClient secretClient;

    public Boolean configPrivateModel(String shopName, UserPrivateTranslateDO data) {
        data.setShopName(shopName);
        //判断这个值在DB中是否存在。存在一系列操作后，插入；不存在，更新相关数据
        UserPrivateTranslateDO dbData = userPrivateTranslateRepo.getDataByShopNameAndApiName(shopName, data.getApiName());

        String userKey = getApiKey(shopName, data.getApiName());
        if (dbData != null) {
            // 仅更新 api_model，prompt_word，token_limit ，is_selected
            AppInsightsUtils.trackTrace("configPrivateModel " + shopName + "userKey: " + userKey);
            secretClient.setSecret(userKey, data.getApiKey());
            return userPrivateTranslateRepo.updateDataByShopNameAndApiName(shopName, data.getApiName(), data.getTokenLimit());

        }

        //将数据存到Azure服务器里面
        AppInsightsUtils.trackTrace("configPrivateModel " + shopName + " userKey: " + userKey);
        KeyVaultSecret keyVaultSecret = secretClient.setSecret(userKey, data.getApiKey());

        // 将数据存到数据库中
        data.setApiKey(userKey);
        boolean save = userPrivateTranslateRepo.save(data);
        return keyVaultSecret != null && save;
    }

    private static String getApiKey(String userName, Integer apiName) {
        //修改userName，将.号处理掉
        userName = userName.replace(".", "");
        return userName + "-" + apiName;
    }

    public UserPrivateTranslateDO getUserPrivateData(String shopName, Integer apiName) {
        // 从数据中获取相关数据
        UserPrivateTranslateDO one = userPrivateTranslateRepo.getDataByShopNameAndApiName(shopName, apiName);

        if (one == null) {
            return null;
        }
        // 处理key后，存放到UserPrivateTranslateDO
        one.setApiKey(null);
        return one;
    }

    public UserPrivateTranslateDO getEnabledPrivateData(String shopName, Integer apiName) {
        UserPrivateTranslateDO data = null;
        if (apiName != null) {
            data = userPrivateTranslateRepo.getDataByShopNameAndApiName(shopName, apiName);
        }

        if (data == null) {
            return null;
        }

        // apiStatus 为空时, 用户将私有key关闭，即不可用
        if (Boolean.FALSE.equals(data.getApiStatus())) {
            return null;
        }
        return data;
    }

    public String getPrivateApiKey(String shopName, Integer apiName) {
        UserPrivateTranslateDO data = getEnabledPrivateData(shopName, apiName);
        if (data == null || data.getApiKey() == null) {
            return null;
        }
        try {
            KeyVaultSecret keyVaultSecret = secretClient.getSecret(data.getApiKey());
            return keyVaultSecret == null ? null : keyVaultSecret.getValue();
        } catch (Exception e) {
            AppInsightsUtils.trackException(e);
            AppInsightsUtils.trackTrace("FatalException getPrivateApiKey failed, shopName: " + shopName
                    + " apiName: " + apiName);
            return null;
        }
    }

    public boolean configPrivateModelExceptApiKey(String shopName, UserPrivateTranslateDO data) {
        data.setShopName(shopName);

        // 判断这个值在DB中是否存在。存在一系列操作后，插入；不存在，更新相关数据
        UserPrivateTranslateDO dbData = userPrivateTranslateRepo.getDataByShopNameAndApiName(shopName, data.getApiName());
        String userKey = getApiKey(shopName, data.getApiName());
        if (dbData != null) {
            // 仅更新 api_model，prompt_word，token_limit ，is_selected
            AppInsightsUtils.trackTrace("configPrivateModelExceptApiKey " + shopName + " userKey: " + userKey);
            return userPrivateTranslateRepo.updateDataByShopNameAndApiName(shopName, data.getApiName(), data.getTokenLimit());
        }

        // 将数据存到数据库中
        data.setApiKey(userKey);
        return userPrivateTranslateRepo.save(data);
    }
}
