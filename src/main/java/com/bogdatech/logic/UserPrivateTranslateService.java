package com.bogdatech.logic;

import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.bogdatech.Service.IUserPrivateTranslateService;
import com.bogdatech.entity.DO.UserPrivateTranslateDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.UserPrivateUtils.getApiKey;

@Component
public class UserPrivateTranslateService {
    @Autowired
    private IUserPrivateTranslateService iUserPrivateTranslateService;
    @Autowired
    private SecretClient secretClient;

    public Boolean configPrivateModel(String shopName, UserPrivateTranslateDO data) {
        data.setShopName(shopName);
        //判断这个值在DB中是否存在。存在一系列操作后，插入；不存在，更新相关数据
        UserPrivateTranslateDO dbData = iUserPrivateTranslateService.getPrivateDataByShopNameAndApiName(shopName, data.getApiName());

        String userKey = getApiKey(shopName, data.getApiName());
        if (dbData != null) {
            // 仅更新 api_model，prompt_word，token_limit ，is_selected
            appInsights.trackTrace("configPrivateModel " + shopName + "userKey: " + userKey);
            KeyVaultSecret keyVaultSecret = secretClient.setSecret(userKey, data.getApiKey());
            return iUserPrivateTranslateService.updateUserDataByShopNameAndApiName(shopName, data.getApiName()
                    , data.getApiModel(), data.getPromptWord(), data.getTokenLimit(), data.getIsSelected());
        }

        //将数据存到Azure服务器里面
        appInsights.trackTrace("configPrivateModel " + shopName + " userKey: " + userKey);
        KeyVaultSecret keyVaultSecret = secretClient.setSecret(userKey, data.getApiKey());

        // 将数据存到数据库中
        data.setApiKey(userKey);
        boolean save = iUserPrivateTranslateService.save(data);
        return keyVaultSecret != null && save;
    }


    public UserPrivateTranslateDO getUserPrivateData(String shopName, Integer apiName) {
        // 从数据中获取相关数据
        UserPrivateTranslateDO one = iUserPrivateTranslateService.getPrivateDataByShopNameAndApiName(shopName, apiName);

        if (one == null) {
            return null;
        }
        // 处理key后，存放到UserPrivateTranslateDO
        one.setApiKey(null);
        return one;
    }

    public boolean configPrivateModelExceptApiKey(String shopName, UserPrivateTranslateDO data) {
        data.setShopName(shopName);

        // 判断这个值在DB中是否存在。存在一系列操作后，插入；不存在，更新相关数据
        UserPrivateTranslateDO dbData = iUserPrivateTranslateService.getPrivateDataByShopNameAndApiName(shopName, data.getApiName());

        String userKey = getApiKey(shopName, data.getApiName());
        if (dbData != null) {
            // 仅更新 api_model，prompt_word，token_limit ，is_selected
            appInsights.trackTrace("configPrivateModelExceptApiKey " + shopName + " userKey: " + userKey);
            return iUserPrivateTranslateService.updateUserDataByShopNameAndApiName(shopName, data.getApiName()
                    , data.getApiModel(), data.getPromptWord(), data.getTokenLimit(), data.getIsSelected());
        }

        // 将数据存到数据库中
        data.setApiKey(userKey);
        return iUserPrivateTranslateService.save(data);
    }
}
