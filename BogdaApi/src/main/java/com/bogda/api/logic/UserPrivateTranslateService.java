package com.bogda.api.logic;

import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bogda.api.Service.IUserPrivateTranslateService;
import com.bogda.api.entity.DO.UserPrivateTranslateDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import static com.bogda.common.utils.CaseSensitiveUtils.appInsights;

@Service
public class UserPrivateTranslateService {
    @Autowired
    private IUserPrivateTranslateService iUserPrivateTranslateService;
    @Autowired
    private SecretClient secretClient;

    public Boolean configPrivateModel(String shopName, UserPrivateTranslateDO data) {
        data.setShopName(shopName);
        //判断这个值在DB中是否存在。存在一系列操作后，插入；不存在，更新相关数据
        UserPrivateTranslateDO dbData = iUserPrivateTranslateService.getOne(new LambdaQueryWrapper<UserPrivateTranslateDO>()
                .eq(UserPrivateTranslateDO::getShopName, shopName)
                .eq(UserPrivateTranslateDO::getApiName, data.getApiName()));
        String userKey = getApiKey(shopName, data.getApiName());
        if (dbData != null) {
            // 仅更新 api_model，prompt_word，token_limit ，is_selected
            appInsights.trackTrace("configPrivateModel " + shopName + "userKey: " + userKey);
            KeyVaultSecret keyVaultSecret = secretClient.setSecret(userKey, data.getApiKey());
            return iUserPrivateTranslateService.update(new LambdaUpdateWrapper<UserPrivateTranslateDO>()
                    .eq(UserPrivateTranslateDO::getShopName, shopName)
                    .eq(UserPrivateTranslateDO::getApiName, data.getApiName())
                    .set(UserPrivateTranslateDO::getApiModel, data.getApiModel())
                    .set(UserPrivateTranslateDO::getPromptWord, data.getPromptWord())
                    .set(UserPrivateTranslateDO::getTokenLimit, data.getTokenLimit())
                    .set(UserPrivateTranslateDO::getIsSelected, data.getIsSelected()));
        }

        //将数据存到Azure服务器里面
        appInsights.trackTrace("configPrivateModel " + shopName + " userKey: " + userKey);
        KeyVaultSecret keyVaultSecret = secretClient.setSecret(userKey, data.getApiKey());

        // 将数据存到数据库中
        data.setApiKey(userKey);
        boolean save = iUserPrivateTranslateService.save(data);
        return keyVaultSecret != null && save;
    }

    private static String getApiKey(String userName, Integer apiName) {
        //修改userName，将.号处理掉
        userName = userName.replace(".", "");
        return userName + "-" + apiName;
    }

    public UserPrivateTranslateDO getUserPrivateData(String shopName, Integer apiName) {
        // 从数据中获取相关数据
        UserPrivateTranslateDO one = iUserPrivateTranslateService.getOne(new LambdaQueryWrapper<UserPrivateTranslateDO>().eq(UserPrivateTranslateDO::getShopName, shopName).eq(UserPrivateTranslateDO::getApiName, apiName));
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
        UserPrivateTranslateDO dbData = iUserPrivateTranslateService.getOne(new LambdaQueryWrapper<UserPrivateTranslateDO>()
                .eq(UserPrivateTranslateDO::getShopName, shopName)
                .eq(UserPrivateTranslateDO::getApiName, data.getApiName()));
        String userKey = getApiKey(shopName, data.getApiName());
        if (dbData != null) {
            // 仅更新 api_model，prompt_word，token_limit ，is_selected
            appInsights.trackTrace("configPrivateModelExceptApiKey " + shopName + " userKey: " + userKey);
            return iUserPrivateTranslateService.update(new LambdaUpdateWrapper<UserPrivateTranslateDO>()
                    .eq(UserPrivateTranslateDO::getShopName, shopName)
                    .eq(UserPrivateTranslateDO::getApiName, data.getApiName())
                    .set(UserPrivateTranslateDO::getApiModel, data.getApiModel())
                    .set(UserPrivateTranslateDO::getPromptWord, data.getPromptWord())
                    .set(UserPrivateTranslateDO::getTokenLimit, data.getTokenLimit())
                    .set(UserPrivateTranslateDO::getIsSelected, data.getIsSelected()));
        }

        // 将数据存到数据库中
        data.setApiKey(userKey);
        return iUserPrivateTranslateService.save(data);
    }
}
