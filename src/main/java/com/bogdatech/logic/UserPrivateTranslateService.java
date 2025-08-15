package com.bogdatech.logic;

import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bogdatech.Service.IUserPrivateTranslateService;
import com.bogdatech.entity.DO.UserPrivateTranslateDO;
import com.bogdatech.integration.ChatGptByOpenaiIntegration;
import com.bogdatech.integration.PrivateIntegration;
import com.bogdatech.utils.CharacterCountUtils;
import com.openai.client.OpenAIClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.UserPrivateUtils.getApiKey;
import static com.bogdatech.utils.UserPrivateUtils.maskString;

@Service
public class UserPrivateTranslateService {
    private final IUserPrivateTranslateService iUserPrivateTranslateService;
    private final SecretClient secretClient;
    private final ChatGptByOpenaiIntegration chatGptByOpenaiIntegration;
    private final PrivateIntegration privateIntegration;

    @Autowired
    public UserPrivateTranslateService(IUserPrivateTranslateService iUserPrivateTranslateService, SecretClient secretClient, ChatGptByOpenaiIntegration chatGptByOpenaiIntegration, PrivateIntegration privateIntegration) {
        this.iUserPrivateTranslateService = iUserPrivateTranslateService;
        this.secretClient = secretClient;
        this.chatGptByOpenaiIntegration = chatGptByOpenaiIntegration;
        this.privateIntegration = privateIntegration;
    }

    public Boolean configPrivateModel(String shopName, UserPrivateTranslateDO data) {
        data.setShopName(shopName);
        //判断这个值在DB中是否存在。存在一系列操作后，插入；不存在，更新相关数据
        UserPrivateTranslateDO dbData = iUserPrivateTranslateService.getOne(new LambdaQueryWrapper<UserPrivateTranslateDO>()
                .eq(UserPrivateTranslateDO::getShopName, shopName)
                .eq(UserPrivateTranslateDO::getApiName, data.getApiName()));
        String userKey = getApiKey(shopName, data.getApiName());
        if (dbData != null) {
            //仅更新 api_model，prompt_word，token_limit ，is_selected
            appInsights.trackTrace("userKey: " + userKey);
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
        appInsights.trackTrace("userKey: " + userKey);
        KeyVaultSecret keyVaultSecret = secretClient.setSecret(userKey, data.getApiKey());

        //将数据存到数据库中
        data.setApiKey(userKey);
        boolean save = iUserPrivateTranslateService.save(data);
        return keyVaultSecret != null && save;
    }


    public UserPrivateTranslateDO getUserPrivateData(String shopName, Integer apiName) {
        String userKey = getApiKey(shopName, apiName);
        KeyVaultSecret keyVaultSecret = secretClient.getSecret(userKey);
        String keyVaultSecretValue = keyVaultSecret.getValue();
        // 从数据中获取相关数据
        UserPrivateTranslateDO one = iUserPrivateTranslateService.getOne(new LambdaQueryWrapper<UserPrivateTranslateDO>().eq(UserPrivateTranslateDO::getShopName, shopName).eq(UserPrivateTranslateDO::getApiName, apiName));
        // 处理key后，存放到UserPrivateTranslateDO
        String key = maskString(keyVaultSecretValue);
        one.setApiKey(key);
        return one;
    }

    /**
     * TODO： 根据传入的参数，调用不同的模型测试
     */
    public String testPrivateModel(String shopName, Integer apiName, String data, String target) {
        return getGenerateText(shopName, apiName, data, target);
    }

    /**
     * 根据apiName，返回对应的值
     */
    public String getGenerateText(String shopName, Integer apiName, String text, String target) {
        //暂时先写两个，Google和openAI
        //获取用户db中存储的提示词
        UserPrivateTranslateDO userPrivateTranslateDO = iUserPrivateTranslateService.getOne(new LambdaQueryWrapper<UserPrivateTranslateDO>().eq(UserPrivateTranslateDO::getShopName, shopName).eq(UserPrivateTranslateDO::getApiName, apiName));
        Long limitChars = userPrivateTranslateDO.getTokenLimit();
        String systemPrompt = userPrivateTranslateDO.getPromptWord();
        String model = userPrivateTranslateDO.getApiModel();
        //根据数据库的值获取
        String apiKey = userPrivateTranslateDO.getApiKey();
        KeyVaultSecret keyVaultSecret = secretClient.getSecret(apiKey);
        apiKey = keyVaultSecret.getValue();
        String targetText = null;
        switch (apiName) {
            case 0:
                //google
                targetText = privateIntegration.getGoogleTranslationWithRetry(text, apiKey, target, shopName, limitChars);
                break;
            case 1:
                //openAI
                targetText = privateIntegration.translateByGpt(text, model, apiKey, systemPrompt, shopName, limitChars);
                break;
        }
        return targetText;
    }
}
